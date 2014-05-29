package fr.inria.diversify.maven;

import fr.inria.diversify.Main;
import fr.inria.diversify.maven.util.Booter;
import org.apache.maven.index.ArtifactInfo;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.repository.RemoteRepository;

/**
 * User: Simon
 * Date: 6/12/13
 * Time: 3:47 PM
 */
public class DependencyTree {

    public DependencyNode getDependencyTree(ArtifactInfo ai) throws Exception {
        return getDependencyTree(ai.groupId+":"+ai.artifactId+(Main.mergingVersions?"":":"+ai.version));
    }


    public DependencyNode getDependencyTree(String ai) throws Exception {

        RepositorySystem system = Booter.newRepositorySystem();

        RepositorySystemSession session = Booter.newRepositorySystemSession( system );
        Artifact artifact = new DefaultArtifact( ai );

        RemoteRepository repo = Booter.newCentralRepository();

        CollectRequest collectRequest = new CollectRequest();
        collectRequest.setRoot( new Dependency( artifact, "compile" ) );
        collectRequest.addRepository( repo );

        CollectResult collectResult = system.collectDependencies( session, collectRequest );

        return collectResult.getRoot();
    }



}
