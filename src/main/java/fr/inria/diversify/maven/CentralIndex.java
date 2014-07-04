package fr.inria.diversify.maven;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.IndexSearcher;
import org.apache.maven.index.ArtifactInfo;
import org.apache.maven.index.Indexer;
import org.apache.maven.index.context.IndexCreator;
import org.apache.maven.index.context.IndexUtils;
import org.apache.maven.index.context.IndexingContext;
import org.apache.maven.index.updater.*;
import org.apache.maven.wagon.Wagon;
import org.apache.maven.wagon.events.TransferEvent;
import org.apache.maven.wagon.events.TransferListener;
import org.apache.maven.wagon.observers.AbstractTransferListener;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.PlexusContainerException;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

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

    public CentralIndex() throws PlexusContainerException, ComponentLookupException {
        // here we create Plexus container, the Maven default IoC container
        // Plexus falls outside of MI scope, just accept the fact that
        // MI is a Plexus component ;)
        // If needed more info, ask on Maven Users list or Plexus Users list
        // google is your friend!
        this.plexusContainer = new DefaultPlexusContainer();

        // lookup the indexer components from plexus
        this.indexer = plexusContainer.lookup(Indexer.class);
        this.indexUpdater = plexusContainer.lookup(IndexUpdater.class);
        // lookup wagon used to remotely fetch index
        this.httpWagon = plexusContainer.lookup(Wagon.class, "http");

    }

    public void buildCentralIndex() throws ComponentLookupException, IOException {
        File centralLocalCache = new File("target/central-cache");
        File centralIndexDir = new File("target/central-index");

        // Creators we want to use (search for fields it defines)
        List<IndexCreator> indexers = new ArrayList<IndexCreator>();
        indexers.add(plexusContainer.lookup(IndexCreator.class, "min"));
        indexers.add(plexusContainer.lookup(IndexCreator.class, "jarContent"));
        indexers.add(plexusContainer.lookup(IndexCreator.class, "maven-plugin"));

        // Create context for central repository index
        centralContext =
                indexer.createIndexingContext("central-context", "central", centralLocalCache, centralIndexDir,
                        "http://repo1.maven.org/maven2", null, true, true, indexers);

        // Update the index (incremental update will happen if this is not 1st run and files are not deleted)
        // This whole block below should not be executed on every app start, but rather controlled by some configuration
        // since this block will always emit at least one HTTP GET. Central indexes are updated once a week, but
        // other index sources might have different index publishing frequency.
        // Preferred frequency is once a week.
        System.out.println("Updating Index...");
        System.out.println("This might take a while on first run, so please be patient!");
        // Create ResourceFetcher implementation to be used with IndexUpdateRequest
        // Here, we use Wagon based one as shorthand, but all we need is a ResourceFetcher implementation
        TransferListener listener = new AbstractTransferListener() {
            public void transferStarted(TransferEvent transferEvent) {
                System.out.print("  Downloading " + transferEvent.getResource().getName());
            }

            public void transferProgress(TransferEvent transferEvent, byte[] buffer, int length) {
            }

            public void transferCompleted(TransferEvent transferEvent) {
                System.out.println(" - Done");
            }
        };
        ResourceFetcher resourceFetcher = new WagonHelper.WagonFetcher(httpWagon, listener, null, null);

        Date centralContextCurrentTimestamp = centralContext.getTimestamp();
        IndexUpdateRequest updateRequest = new IndexUpdateRequest(centralContext, resourceFetcher);
        IndexUpdateResult updateResult = indexUpdater.fetchAndUpdateIndex(updateRequest);
        if (updateResult.isFullUpdate()) {
            System.out.println("Full update happened!");
        } else if (updateResult.getTimestamp().equals(centralContextCurrentTimestamp)) {
            System.out.println("No update needed, index is up to date!");
        } else {
            System.out.println("Incremental update happened, change covered " + centralContextCurrentTimestamp
                    + " - " + updateResult.getTimestamp() + " period.");
        }
    }

    public int allArtifactSize() throws IOException {
        return centralContext.acquireIndexSearcher().getIndexReader().maxDoc();
    }

    public List<ArtifactInfo> partialArtifactInfo(int startPosition, int endPosition) throws IOException {
        List<ArtifactInfo> list = new ArrayList<ArtifactInfo>();
        final IndexSearcher searcher = centralContext.acquireIndexSearcher();
        try {
            final IndexReader ir = searcher.getIndexReader();
            for (int i = startPosition; i < endPosition; i++) {
                if (!ir.isDeleted(i)) {
                    final Document doc = ir.document(i);
                    list.add(IndexUtils.constructArtifactInfo(doc, centralContext));
                }
            }
        } finally {
            centralContext.releaseIndexSearcher(searcher);
        }
        return list;
    }
}
