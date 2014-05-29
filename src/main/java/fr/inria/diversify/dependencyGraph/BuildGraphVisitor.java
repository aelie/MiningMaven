package fr.inria.diversify.dependencyGraph;

import fr.inria.diversify.Main;
import fr.inria.diversify.maven.util.Booter;
import fr.inria.diversify.parser.JarParser;
import fr.inria.diversify.util.Log;
import org.apache.commons.io.FileUtils;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.DependencyVisitor;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResult;

import java.io.File;
import java.util.Stack;

/**
 * User: Simon
 * Date: 6/13/13
 * Time: 11:13 AM
 */
public class BuildGraphVisitor implements DependencyVisitor {
    protected MavenDependencyGraph graph;
    protected Stack<Artifact> parents;
    protected boolean api;
    protected boolean buildCurrentApi;

    public BuildGraphVisitor(MavenDependencyGraph graph,boolean api) {
        this.graph = graph;
        parents = new Stack<Artifact>();
        this.api = api;
        buildCurrentApi= true;
    }

    @Override
    public boolean visitEnter(DependencyNode node) {
        boolean ret = true;
        if(graph.containNode(node.getArtifact())) {
            buildCurrentApi = false;
            ret = false;
        }

        graph.getNodeFor(node.getArtifact());
        if(!parents.isEmpty())
            graph.addEdge(parents.peek(),node.getArtifact());
        parents.add(node.getArtifact());

        return ret;
    }

    protected void extractApi(MavenDependencyNode node) {
        try {
            File file = resolveArtifact(node.getArtifact());
            JarParser jp = new JarParser(file.getAbsolutePath());
            jp.parse();
            node.setApi(jp.getPublicMethods());
            Log.debug("node: {}",node);
            Log.debug("mb call: {}",jp.getCalls().size());

            for(String call: jp.getCalls()) {
                for(MavenDependencyNode dependency: node.getDependency())  {
                    if(dependency.getApi().contains(call)) {
                        node.addCall(call,dependency);
                        break;
                    }
                }
            }
            FileUtils.forceDelete(file);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean visitLeave(DependencyNode node) {
        if(api && buildCurrentApi) {
            extractApi(graph.getNodeFor(node.getArtifact()));
            buildCurrentApi = true;
        }

        parents.pop();
        return true;
    }

    protected File resolveArtifact(Artifact ai)
            throws Exception
    {

        RepositorySystem system = Booter.newRepositorySystem();

        RepositorySystemSession session = Booter.newRepositorySystemSession( system );

        Artifact artifact = new DefaultArtifact(ai.getGroupId()+":"+ai.getArtifactId()+(Main.mergingVersions?"":":"+ai.getVersion()));

        RemoteRepository repo = Booter.newCentralRepository();

        ArtifactRequest artifactRequest = new ArtifactRequest();
        artifactRequest.setArtifact( artifact );
        artifactRequest.addRepository( repo );

        ArtifactResult artifactResult = system.resolveArtifact( session, artifactRequest );

         return artifactResult.getArtifact().getFile();
    }
}