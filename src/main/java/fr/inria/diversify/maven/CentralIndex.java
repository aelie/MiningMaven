package fr.inria.diversify.maven;

import fr.inria.diversify.util.Log;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.maven.index.*;
import org.apache.maven.index.artifact.Gav;
import org.apache.maven.index.context.IndexCreator;
import org.apache.maven.index.context.IndexUtils;
import org.apache.maven.index.context.IndexingContext;
import org.apache.maven.index.expr.SourcedSearchExpression;
import org.apache.maven.index.expr.UserInputSearchExpression;
import org.apache.maven.index.search.grouping.GAGrouping;
import org.apache.maven.index.updater.*;
import org.apache.maven.wagon.Wagon;
import org.apache.maven.wagon.events.TransferEvent;
import org.apache.maven.wagon.events.TransferListener;
import org.apache.maven.wagon.observers.AbstractTransferListener;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.PlexusContainerException;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.util.StringUtils;
import org.sonatype.aether.util.version.GenericVersionScheme;
import org.sonatype.aether.version.InvalidVersionSpecificationException;
import org.sonatype.aether.version.Version;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

/**
 * User: Simon
 * Date: 6/12/13
 * Time: 4:32 PM
 */
public class CentralIndex {

    private final PlexusContainer plexusContainer;

    private final Indexer indexer;

    private final IndexUpdater indexUpdater;

    private final Wagon httpWagon;

    private IndexingContext centralContext;

    public CentralIndex()
            throws PlexusContainerException, ComponentLookupException
    {
        // here we create Plexus container, the Maven default IoC container
        // Plexus falls outside of MI scope, just accept the fact that
        // MI is a Plexus component ;)
        // If needed more info, ask on Maven Users list or Plexus Users list
        // google is your friend!
        this.plexusContainer = new DefaultPlexusContainer();

        // lookup the indexer components from plexus
        this.indexer = plexusContainer.lookup( Indexer.class );
        this.indexUpdater = plexusContainer.lookup( IndexUpdater.class );
        // lookup wagon used to remotely fetch index
        this.httpWagon = plexusContainer.lookup( Wagon.class, "http" );

    }

    public void buildCentralIndex() throws ComponentLookupException, IOException {
        File centralLocalCache = new File( "target/central-cache" );
        File centralIndexDir = new File( "target/central-index" );

        // Creators we want to use (search for fields it defines)
        List<IndexCreator> indexers = new ArrayList<IndexCreator>();
        indexers.add( plexusContainer.lookup( IndexCreator.class, "min" ) );
        indexers.add( plexusContainer.lookup( IndexCreator.class, "jarContent" ) );
        indexers.add( plexusContainer.lookup( IndexCreator.class, "maven-plugin" ) );

        // Create context for central repository index
        centralContext =
                indexer.createIndexingContext( "central-context", "central", centralLocalCache, centralIndexDir,
                        "http://repo1.maven.org/maven2", null, true, true, indexers );

        // Update the index (incremental update will happen if this is not 1st run and files are not deleted)
        // This whole block below should not be executed on every app start, but rather controlled by some configuration
        // since this block will always emit at least one HTTP GET. Central indexes are updated once a week, but
        // other index sources might have different index publishing frequency.
        // Preferred frequency is once a week.
        if ( true )
        {
            System.out.println( "Updating Index..." );
            System.out.println( "This might take a while on first run, so please be patient!" );
            // Create ResourceFetcher implementation to be used with IndexUpdateRequest
            // Here, we use Wagon based one as shorthand, but all we need is a ResourceFetcher implementation
            TransferListener listener = new AbstractTransferListener()
            {
                public void transferStarted( TransferEvent transferEvent )
                {
                    System.out.print( "  Downloading " + transferEvent.getResource().getName() );
                }

                public void transferProgress( TransferEvent transferEvent, byte[] buffer, int length )
                {
                }

                public void transferCompleted( TransferEvent transferEvent )
                {
                    System.out.println( " - Done" );
                }
            };
            ResourceFetcher resourceFetcher = new WagonHelper.WagonFetcher( httpWagon, listener, null, null );

            Date centralContextCurrentTimestamp = centralContext.getTimestamp();
            IndexUpdateRequest updateRequest = new IndexUpdateRequest( centralContext, resourceFetcher );
            IndexUpdateResult updateResult = indexUpdater.fetchAndUpdateIndex( updateRequest );
            if ( updateResult.isFullUpdate() )
            {
                System.out.println( "Full update happened!" );
            }
            else if ( updateResult.getTimestamp().equals( centralContextCurrentTimestamp ) )
            {
                System.out.println( "No update needed, index is up to date!" );
            }
            else
            {
                System.out.println( "Incremental update happened, change covered " + centralContextCurrentTimestamp
                        + " - " + updateResult.getTimestamp() + " period." );
            }

        }
    }

    public List<ArtifactInfo> allArtifactInfo() throws IOException {
        List<ArtifactInfo> list = new ArrayList<ArtifactInfo>();
        final IndexSearcher searcher = centralContext.acquireIndexSearcher();
        try {
            final IndexReader ir = searcher.getIndexReader();
            for ( int i = 0; i < ir.maxDoc(); i++ )
            {
                if ( !ir.isDeleted( i ) )
                {
                    final Document doc = ir.document( i );
                    list.add(IndexUtils.constructArtifactInfo(doc, centralContext));
                }
            }
        }
        finally
        {
            centralContext.releaseIndexSearcher( searcher );
        }
        return list;
    }

    public int allArtifactSize() throws IOException {
        return centralContext.acquireIndexSearcher().getIndexReader().maxDoc();
    }

    public List<ArtifactInfo> partialArtifactInfo(int startPosition, int endPosition) throws IOException {
        List<ArtifactInfo> list = new ArrayList<ArtifactInfo>();
        final IndexSearcher searcher = centralContext.acquireIndexSearcher();
        try {
            final IndexReader ir = searcher.getIndexReader();
            for ( int i = startPosition; i < endPosition; i++ )
            {
                if ( !ir.isDeleted( i ) )
                {
                    final Document doc = ir.document( i );
                    list.add(IndexUtils.constructArtifactInfo(doc, centralContext));
                }
            }
        }
        finally
        {
            centralContext.releaseIndexSearcher( searcher );
        }
        return list;
    }

    public void perform()
            throws IOException, ComponentLookupException, InvalidVersionSpecificationException
    {
        buildCentralIndex();



        // ====
        // Case:
        // dump all the GAVs
        // NOTE: will not actually execute do this below, is too long to do (Central is HUGE), but is here as code
        // example
        if ( false )
        {
            allArtifactInfo();
        }
    }

    public void example() throws InvalidVersionSpecificationException, IOException {
        // ====
        // Case:
        // Search for all GAVs with known G and A and having version greater than V

        final GenericVersionScheme versionScheme = new GenericVersionScheme();
        final String versionString = "1.5.0";
        final Version version = versionScheme.parseVersion( versionString );

        // construct the query for known GA
        final Query groupIdQ =
                indexer.constructQuery( MAVEN.GROUP_ID, new SourcedSearchExpression( "org.sonatype.nexus" ) );
        final Query artifactIdQ =
                indexer.constructQuery( MAVEN.ARTIFACT_ID, new SourcedSearchExpression( "nexus-api" ) );
        final BooleanQuery query = new BooleanQuery();
        query.add( groupIdQ, BooleanClause.Occur.MUST );
        query.add( artifactIdQ, BooleanClause.Occur.MUST );

        // we want "jar" artifacts only
        query.add( indexer.constructQuery( MAVEN.PACKAGING, new SourcedSearchExpression( "jar" ) ), BooleanClause.Occur.MUST );
        // we want main artifacts only (no classifier)
        // Note: this below is unfinished API, needs fixing
        query.add( indexer.constructQuery( MAVEN.CLASSIFIER, new SourcedSearchExpression( Field.NOT_PRESENT ) ),
                BooleanClause.Occur.MUST_NOT );

        // construct the filter to express "V greater than"
        final ArtifactInfoFilter versionFilter = new ArtifactInfoFilter()
        {
            public boolean accepts( final IndexingContext ctx, final ArtifactInfo ai )
            {
                try
                {
                    final Version aiV = versionScheme.parseVersion( ai.version );
                    // Use ">=" if you are INCLUSIVE
                    return aiV.compareTo( version ) > 0;
                }
                catch ( InvalidVersionSpecificationException e )
                {
                    // do something here? be safe and include?
                    return true;
                }
            }
        };

        System.out.println( "Searching for all GAVs with G=org.sonatype.nexus and nexus-api and having V greater than 1.5.0" );
        final IteratorSearchRequest request =
                new IteratorSearchRequest( query, Collections.singletonList(centralContext), versionFilter );
        final IteratorSearchResponse response = indexer.searchIterator( request );
        for ( ArtifactInfo ai : response )
        {
            System.out.println( ai.toString() );
        }

        // Case:
        // Use index
        // Searching for some artifact
        Query gidQ = indexer.constructQuery( MAVEN.GROUP_ID, new SourcedSearchExpression( "org.apache.diversify.indexer" ) );
        Query aidQ = indexer.constructQuery( MAVEN.ARTIFACT_ID, new SourcedSearchExpression( "indexer-artifact" ) );

        BooleanQuery bq = new BooleanQuery();
        bq.add( gidQ, BooleanClause.Occur.MUST );
        bq.add( aidQ, BooleanClause.Occur.MUST );

        searchAndDump( indexer, "all artifacts under GA org.apache.diversify.indexer:indexer-artifact", bq );

        // Searching for some main artifact
        bq = new BooleanQuery();
        bq.add( gidQ, BooleanClause.Occur.MUST );
        bq.add( aidQ, BooleanClause.Occur.MUST );
        // bq.add( nexusIndexer.constructQuery( MAVEN.CLASSIFIER, new SourcedSearchExpression( "*" ) ), Occur.MUST_NOT
        // );

        searchAndDump( indexer, "main artifacts under GA org.apache.diversify.indexer:indexer-artifact", bq );

        // doing sha1 search
        searchAndDump( indexer, "SHA1 7ab67e6b20e5332a7fb4fdf2f019aec4275846c2", indexer.constructQuery( MAVEN.SHA1,
                new SourcedSearchExpression( "7ab67e6b20e5332a7fb4fdf2f019aec4275846c2" ) ) );

        searchAndDump( indexer, "SHA1 7ab67e6b20 (partial hash)",
                indexer.constructQuery( MAVEN.SHA1, new UserInputSearchExpression( "7ab67e6b20" ) ) );

        // doing classname search (incomplete classname)
        searchAndDump( indexer, "classname DefaultNexusIndexer (note: Central does not publish classes in the index)",
                indexer.constructQuery( MAVEN.CLASSNAMES, new UserInputSearchExpression( "DefaultNexusIndexer" ) ) );

        // doing search for all "canonical" diversify plugins latest versions
        bq = new BooleanQuery();
        bq.add( indexer.constructQuery( MAVEN.PACKAGING, new SourcedSearchExpression( "diversify-plugin" ) ), BooleanClause.Occur.MUST );
        bq.add( indexer.constructQuery( MAVEN.GROUP_ID, new SourcedSearchExpression( "org.apache.diversify.plugins" ) ),
                BooleanClause.Occur.MUST );
        searchGroupedAndDump( indexer, "all \"canonical\" diversify plugins", bq, new GAGrouping() );

        // close cleanly
        indexer.closeIndexingContext( centralContext, false );
    }

    public void searchAndDump( Indexer nexusIndexer, String descr, Query q )
            throws IOException
    {
        System.out.println( "Searching for " + descr );

        FlatSearchResponse response = nexusIndexer.searchFlat( new FlatSearchRequest( q, centralContext ) );

        for ( ArtifactInfo ai : response.getResults() )
        {

            System.out.println( ai.toString() );
        }

        System.out.println( "------" );
        System.out.println( "Total: " + response.getTotalHitsCount() );
        System.out.println();
    }

    public void searchGroupedAndDump( Indexer nexusIndexer, String descr, Query q, Grouping g )
            throws IOException
    {
        System.out.println( "Searching for " + descr );

        GroupedSearchResponse response = nexusIndexer.searchGrouped( new GroupedSearchRequest( q, g, centralContext ) );

        for ( Map.Entry<String, ArtifactInfoGroup> entry : response.getResults().entrySet() )
        {
            ArtifactInfo ai = entry.getValue().getArtifactInfos().iterator().next();
            System.out.println( "* Plugin " + ai.artifactId );
            System.out.println( "  Latest version:  " + ai.version );

            System.out.println( StringUtils.isBlank(ai.description) ? "No description in plugin's POM."
                    : StringUtils.abbreviate( ai.description, 60 ) );
                System.out.println(ai);
        }

        System.out.println( "------" );
        System.out.println( "Total record hits: " + response.getTotalHitsCount() );
        System.out.println();
    }
}
