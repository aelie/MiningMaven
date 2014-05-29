package fr.inria.diversify.dependencyGraph;

import fr.inria.diversify.Main;
import fr.inria.diversify.maven.DependencyTree;
import fr.inria.diversify.maven.util.LogDependencyGraphDumper;
import fr.inria.diversify.util.Log;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.graph.DependencyNode;
import org.json.JSONArray;
import org.json.JSONException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * User: Simon
 * Date: 6/17/13
 * Time: 2:22 PM
 */
public class GraphBuilder {

    public MavenDependencyGraph buildGraphDependency(List<String> artifacts) {
        Set<String> mark = new HashSet<String>();
        MavenDependencyGraph graph = new MavenDependencyGraph();
        int i = 0;
        for(String ai : artifacts) {
            Log.debug("count: " + i);
            i++;
            if(!mark.contains(ai)) {
                DependencyTree dt = new DependencyTree();
                try {
                    DependencyNode tree = dt.getDependencyTree(ai);
                    tree.accept(new LogDependencyGraphDumper());
//                    tree.accept(new BuildGraphVisitor(graph, true));
                        tree.accept(new BuildGraphVisitor(graph, false));

                    mark.addAll(allChildren(tree));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        return graph;
    }


    public MavenDependencyGraph forJSONFiles(String dir) throws JSONException, IOException {
        File file = new File(dir);
        MavenDependencyGraph dependencyGraph = new MavenDependencyGraph();
        for (File f : file.listFiles())  {

            if(f.getName().endsWith(".json")) {
                Log.debug("load file: {}",f.getName());
                BufferedReader br = new BufferedReader(new FileReader(f));
                StringBuffer sb = new StringBuffer();
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

    protected Set<String> allChildren(DependencyNode node) {

        Set<String> children = new HashSet<String>();
        for(DependencyNode n : node.getChildren()) {
            Artifact a = n.getArtifact();
            children.add(a.getGroupId()+":"+a.getArtifactId()+(Main.mergingVersions?"":":"+a.getVersion()));
            children.addAll(allChildren(n));
        }
        return children;
    }
}
