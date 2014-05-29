package fr.inria.diversify.dependencyGraph;

import fr.inria.diversify.Main;
import org.eclipse.aether.artifact.Artifact;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.*;

/**
 * User: Simon
 * Date: 6/13/13
 * Time: 11:10 AM
 */
public class MavenDependencyNode {

    protected Artifact ai;
    protected Set<MavenDependencyNode> directDependency;
    protected Set<String> api;
    protected int apiSize;
    protected Map<MavenDependencyNode,Set<String>> callsToDependency;

    public MavenDependencyNode(Artifact ai) {
        this.ai = ai;
        directDependency = new HashSet<MavenDependencyNode>();
        api = new HashSet<String>();
        callsToDependency = new HashMap<MavenDependencyNode, Set<String>>();
    }

    public void addDependency(MavenDependencyNode node) {
        directDependency.add(node);
    }

    public void setApi(Set<String> api) {
        apiSize = api.size();
        this.api = api;
    }

    protected void toDot(StringBuffer bf) {
        bf.append(this.hashCode()+ " [label=\""+this+"\"];\n");
        for(MavenDependencyNode node : directDependency) {
            if(callsToDependency.get(node) != null)
                bf.append(this.hashCode() + " -> " +node.hashCode()+" [label=\""+callsToDependency.get(node).size()+"\"];\n");
            else
                bf.append(this.hashCode() + " -> " +node.hashCode()+";\n");
        }
    }

    public Artifact getArtifact() {
        return ai;
    }

    public Set<MavenDependencyNode> getDependency() {
        return directDependency;
    }

    public Set<MavenDependencyNode> getAllDependency() {
        Set<MavenDependencyNode> dep = new HashSet<MavenDependencyNode>();
        for(MavenDependencyNode n : directDependency)
            dep.addAll(n.getAllDependency());

        dep.addAll(directDependency);
        return dep;
    }

    public Set<String> getApi() {
        return api;
    }

    public int getApiSize() {
        return apiSize;
    }

    public void setApiSize(int size) {
        apiSize = size;
    }

    public void addCall(String call, MavenDependencyNode dependency) {
        if(!callsToDependency.containsKey(dependency))
            callsToDependency.put(dependency,new HashSet<String>());

        callsToDependency.get(dependency).add(call);
    }

    @Override
    public String toString() {
        return  ai.getGroupId()+":"+ai.getArtifactId()+(Main.mergingVersions?"":":"+ai.getVersion());
    }



    public JSONObject toJSON() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("Artifact",this.toString());
//        json.put("API",new JSONArray(api));
        json.put("API",apiSize);
        json.put("Dependency", dependencyJSON());
        return json;
    }

    protected List<JSONObject> dependencyJSON() throws JSONException {
        List<JSONObject> list = new ArrayList<JSONObject>(directDependency.size());
        for (MavenDependencyNode dependency : directDependency) {
            JSONObject json = new JSONObject();
            json.put("Artifact", dependency.toString());
            json.put("APICall", callsToDependency.get(dependency));
            list.add(json);
        }
        return list;
    }

}
