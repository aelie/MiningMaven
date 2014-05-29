package fr.inria.diversify.dependencyGraph;

import fr.inria.diversify.Main;
import fr.inria.diversify.maven.FakeArtifact;
import org.eclipse.aether.artifact.Artifact;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

/**
 * User: Simon
 * Date: 6/13/13
 * Time: 11:37 AM
 */
public class MavenDependencyGraph {

    protected Map<String, MavenDependencyNode> nodes;
    protected static Map<String,Integer> stringId;
    protected static Integer count = 0;
    public MavenDependencyGraph() {
        nodes = new HashMap<String, MavenDependencyNode>();
    }

    public void addGraph(JSONArray array) throws JSONException {
        for(int i = 0; i < array.length(); i++) {
            JSONObject obj = array.getJSONObject(i);
            Artifact artifact = new FakeArtifact(obj.getString("Artifact"));
            MavenDependencyNode node = getNodeFor(artifact);
            node.setApiSize(obj.getInt("API"));
            JSONArray dep = obj.getJSONArray("Dependency");
            for(int j = 0; j < dep.length(); j++) {
                Artifact artifactDep = new FakeArtifact(dep.getJSONObject(j).getString("Artifact"));
                MavenDependencyNode nodeDep = getNodeFor(artifactDep);
                addEdge(artifact,artifactDep);
                JSONArray call = dep.getJSONObject(j).getJSONArray("APICall");
                for(int k = 0; k < call.length(); k++)
                    node.addCall(call.getString(k) ,nodeDep);
            }

        }
    }



    public void addEdge(Artifact a1, Artifact a2) {
        MavenDependencyNode n1 = getNodeFor(a1);
        MavenDependencyNode n2 = getNodeFor(a2);

        n1.addDependency(n2);
    }

    public boolean containNode(Artifact ai) {
       return nodes.containsKey(ai.getGroupId()+":"+ai.getArtifactId()+(Main.mergingVersions?"":":"+ai.getVersion()));

    }

    public MavenDependencyNode getNodeFor(Artifact ai) {
        String id = ai.getGroupId()+":"+ai.getArtifactId()+(Main.mergingVersions?"":":"+ai.getVersion());
        if(nodes.containsKey(id))
            return nodes.get(id);
        else {
            MavenDependencyNode n = new MavenDependencyNode(ai);
            nodes.put(id,n);
            return n;
        }
    }

    public JSONArray toJSON() {
        JSONArray json = new JSONArray();

        for (MavenDependencyNode node : nodes.values())
            try {
                json.put(node.toJSON());
            } catch (JSONException e) {
                e.printStackTrace();
            }
        return  json;
    }

    public void toJSONObjectIn(String fileName) throws IOException {
        FileWriter fw = new FileWriter(fileName);
        BufferedWriter bw = new BufferedWriter(fw);
        bw.write(toJSON().toString());
        bw.close();
    }

    public String toDot() {
        StringBuffer bf = new StringBuffer();
        bf.append("digraph G {\n");

        for(MavenDependencyNode node: nodes.values()) {
           node.toDot(bf);
        }
        bf.append("}");
        return bf.toString();
    }


    public String info() {
        return "number of node: "+nodes.size();
    }

    protected static Integer stringToId(String string) {
        if(stringId == null)
            stringId = new HashMap<String, Integer>();

        if(!stringId.containsKey(string)) {
            stringId.put(string,count);
            count++;
        }
        return stringId.get(string);
    }
////    todo
////    move all this methods in Stat
//    //the distribution of artifact in the graph dependency
//    public Map<String,Integer> artifactDistribution() {
//        Map<String,Integer> map = new HashMap<String, Integer>();
//        for(MavenDependencyNode n  : nodes.values()) {
//            String key = n.getArtifact().getGroupId() + ":" + n.getArtifact().getArtifactId();
//            if(!map.containsKey(key))
//                map.put(key,0);
//            map.put(key,map.get(key)+1);
//        }
//        return map;
//    }
//
//    //number of dependency for each node
//    public Map<String,Integer> artifactDependencyDistribution(boolean all) {
//        Map<String,Integer> map = new HashMap<String, Integer>();
//        for(MavenDependencyNode n  : nodes.values()) {
//            String key = n.getArtifact().getGroupId() + ":" + n.getArtifact().getArtifactId();
//            if(!map.containsKey(key))
//                map.put(key,0);
//            if(all)
//                map.put(key,map.get(key)+n.getAllDependency().size());
//            else
//                map.put(key,map.get(key)+n.getDependency().size());
//        }
//        return map;
//    }
//
//    //for each dependency, his number of user
//    public Map<String,Integer> artifactUsedDistribution(boolean all) {
//        Map<String,Integer> map = new HashMap<String, Integer>();
//        for(MavenDependencyNode n  : nodes.values()) {
//            Set<MavenDependencyNode> dep;
//            if(all)
//                dep = n.getAllDependency();
//            else
//                dep = n.getDependency();
//            for(MavenDependencyNode u : dep) {
//                String key = u.getArtifact().getGroupId() + ":" + u.getArtifact().getArtifactId();
//                if(!map.containsKey(key))
//                    map.put(key,0);
//                map.put(key,map.get(key)+1);
//            }
//        }
//        return map;
//    }

    public Map<String,Set<MavenDependencyNode>> dependencyDistribution(boolean all) {
        return dependencyDistribution(nodes.values(), all);
    }

    public Map<String,Set<MavenDependencyNode>> dependencyDistribution(String  groupId, String artifactId, boolean all) {
        List<MavenDependencyNode> list = new ArrayList<MavenDependencyNode>();

        for(MavenDependencyNode n : nodes.values()) {
            if((groupId == null || n.getArtifact().getGroupId().equals(groupId)) &&
                    (artifactId == null || n.getArtifact().getArtifactId().equals(artifactId)))
                list.add(n);
        }
        return dependencyDistribution(list,all);
    }


    public Map<String,Set<MavenDependencyNode>> dependencyUsedDistribution(boolean all) {
        return dependencyUsedDistribution(nodes.values(), all);
    }

    public Map<String,Set<MavenDependencyNode>> dependencyUsedDistribution(String  groupId, String artifactId, boolean all) {
        List<MavenDependencyNode> list = new ArrayList<MavenDependencyNode>();

        for(MavenDependencyNode n : nodes.values()) {
            if((groupId == null || n.getArtifact().getGroupId().equals(groupId)) &&
                    (artifactId == null || n.getArtifact().getArtifactId().equals(artifactId)))
                list.add(n);
        }
        return dependencyUsedDistribution(list, all);
    }

    protected Map<String,Set<MavenDependencyNode>> dependencyDistribution(Collection<MavenDependencyNode> listOfNode, boolean all) {
        Map<String,Set<MavenDependencyNode>> distribution = new HashMap<String, Set<MavenDependencyNode>>(listOfNode.size());
        for (MavenDependencyNode n : listOfNode) {
            if(all)
                distribution.put(n.getArtifact().getVersion(),n.getAllDependency());
            else
                distribution.put(n.getArtifact().getVersion(),n.getDependency());
        }
        return  distribution;
    }


    protected Map<String,Set<MavenDependencyNode>> dependencyUsedDistribution(Collection<MavenDependencyNode> listOfNode, boolean all) {
        Map<String,Set<MavenDependencyNode>> map = new HashMap<String,Set<MavenDependencyNode>>();
        for (MavenDependencyNode n : nodes.values()) {
            Set<MavenDependencyNode> dep;
            if(all)
                dep = n.getAllDependency();
            else
            dep = n.getDependency();
            for(MavenDependencyNode u : dep) {
                if(listOfNode.contains(u)) {
                    String key = u.getArtifact().getVersion();
                    if(!map.containsKey(key))
                        map.put(key,new HashSet<MavenDependencyNode>());
                    map.get(key).add(n);
                }
            }

        }
        return map;
    }


    public Map<String, MavenDependencyNode> getNodes() {
        return nodes;
    }
}
