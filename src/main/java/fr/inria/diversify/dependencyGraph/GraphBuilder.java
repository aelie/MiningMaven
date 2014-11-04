package fr.inria.diversify.dependencyGraph;

import fr.inria.diversify.Main;
import fr.inria.diversify.maven.DependencyTree;
import fr.inria.diversify.maven.util.Booter;
import fr.inria.diversify.util.Log;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.resolution.ArtifactDescriptorException;
import org.eclipse.aether.resolution.ArtifactDescriptorRequest;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;
import org.json.JSONArray;
import org.json.JSONException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * User: Simon
 * Date: 6/17/13
 * Time: 2:22 PM
 */
public class GraphBuilder {

    static int i = 0;

    protected static Set<String> allChildren(DependencyNode node) {

        Set<String> children = new HashSet<String>();
        for (DependencyNode n : node.getChildren()) {
            Artifact a = n.getArtifact();
            children.add(a.getGroupId() + ":" + a.getArtifactId() + (Main.mergingVersions ? "" : ":" + a.getVersion()));
            children.addAll(allChildren(n));
        }
        return children;
    }

    public List<Dependency> getDirectDependencies(String artifactAsString) {
        RepositorySystem system = Booter.newRepositorySystem();
        RepositorySystemSession session = Booter.newRepositorySystemSession(system);
        Artifact artifact = new DefaultArtifact(artifactAsString /*"org.eclipse.aether:aether-impl:0.9.0.M3"*/);
        ArtifactDescriptorRequest descriptorRequest = new ArtifactDescriptorRequest();
        descriptorRequest.setArtifact(artifact);
        descriptorRequest.setRepositories(Booter.newRepositories(system, session));
        ArtifactDescriptorResult descriptorResult = null;
        try {
            descriptorResult = system.readArtifactDescriptor(session, descriptorRequest);
        } catch (ArtifactDescriptorException e) {
            Log.error("ArtifactDescriptorException for " + artifactAsString);
            return new ArrayList<>();
        }
        return descriptorResult.getDependencies();
    }

    public MavenDependencyGraph buildGraphDependency(List<String> artifacts) {
        Set<String> mark = new HashSet<String>();
        MavenDependencyGraph graph = new MavenDependencyGraph();
        i = 0;
        for (int j = 0; j < artifacts.size() / 100; j++) {
            artifacts.subList(j * 100, Math.min((j + 1) * 100, artifacts.size()))
                    .parallelStream()
                    .map(ai -> {
                        if (!mark.contains(ai)) {
                            Log.debug("count: " + i++);
                            DependencyTree dt = new DependencyTree();
                            try {
                                DependencyNode tree = dt.getDependencyTree(ai);
                                //tree.accept(new LogDependencyGraphDumper());
                                return tree;
                            } catch (Exception e) {
                                e.printStackTrace();
                                return null;
                            }
                        }
                        return null;
                    })
                    .filter(tree -> tree != null)
                    .sequential()
                    .forEach(tree -> {
                        mark.addAll(allChildren(tree));
                        tree.accept(new BuildGraphVisitor(graph, false));
                    });
        }
        System.out.println(graph.getNodes().size());
        System.out.println("M" + mark.size());
        System.out.println("-----------------------------------------------------------");
        mark.clear();
        i = 0;
        MavenDependencyGraph graph2 = new MavenDependencyGraph();
        for (String ai : artifacts) {
            if (!mark.contains(ai)) {
                Log.debug("count: " + i++);
                DependencyTree dt = new DependencyTree();
                try {
                    DependencyNode tree = dt.getDependencyTree(ai);
                    //tree.accept(new LogDependencyGraphDumper());
//                    tree.accept(new BuildGraphVisitor(graph, true));
                    synchronized (graph2) {
                        tree.accept(new BuildGraphVisitor(graph2, false));
                    }
                    synchronized (mark) {
                        mark.addAll(allChildren(tree));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        System.out.println("M" + mark.size());
        //System.out.println(graph2.equals(graph));
        System.out.println(graph.getNodes().size());
        System.out.println(graph2.getNodes().size());
        return graph;
    }

    public MavenDependencyGraph forJSONFiles(String dir) throws JSONException, IOException {
        File file = new File(dir);
        MavenDependencyGraph dependencyGraph = new MavenDependencyGraph();
        for (File f : file.listFiles()) {

            if (f.getName().endsWith(".json")) {
                Log.debug("load file: {}", f.getName());
                BufferedReader br = new BufferedReader(new FileReader(f));
                StringBuilder sb = new StringBuilder();
                String line = br.readLine();
                while (line != null) {
                    sb.append(line);
                    line = br.readLine();
                }
                if (sb.length() != 0)
                    dependencyGraph.addGraph(new JSONArray(sb.toString()));
            }
        }
        return dependencyGraph;
    }
}
