package fr.inria.diversify.stat;

import fr.inria.diversify.dependencyGraph.MavenDependencyGraph;
import fr.inria.diversify.dependencyGraph.MavenDependencyNode;
import fr.inria.diversify.util.Log;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

/**
 * User: Simon
 * Date: 10/14/13
 * Time: 11:01 AM
 */
public class Stat2 {
    protected MavenDependencyGraph dependencyGraph;
    protected String fileName;

    public Stat2(MavenDependencyGraph dependencyGraph, String fileName) {
        this.dependencyGraph = dependencyGraph;
        this.fileName = fileName;
    }

    public void writeGeneralStat(String title) throws IOException {
        Log.info("number of artifact: {}",artifactDistribution().size());
        writeNormalizedData("dependencyDistribution" + "_" + title, artifactDependencyDistribution(false));
        writeNormalizedData("artifactUsedDistribution" + "_" + title, artifactUsedDistribution(false));
        writeData("dependencies" + "_" + title, artifactDependencyDistribution(false));
        writeData("usages" + "_" + title, artifactUsedDistribution(false));
    }

    /**
     * compute the artifact distribution in the graph
     */
    public Map<String,Integer> artifactDistribution() {
        Map<String,Integer> map = new HashMap<String, Integer>();
        for(MavenDependencyNode n  : dependencyGraph.getNodes().values()) {
            String key = n.getArtifact().getGroupId() + ":" + n.getArtifact().getArtifactId();
            if(!map.containsKey(key))
                map.put(key,0);
            map.put(key,map.get(key)+1);

        }
        return map;
    }

    /**
     * compute the artifact dependency distribution in the graph
     * @param transitiveDep adds (or not) the transitive dependency
     * @return
     */
    public Map<String,Set<String>> artifactDependencyDistribution(boolean transitiveDep) {
        Map<String,Set<String>> dependenciesByArtifact = new HashMap<String, Set<String>>();
        for(MavenDependencyNode dependencyNode  : dependencyGraph.getNodes().values()) {
            String artifactKey =  dependencyNode.getArtifact().getGroupId() + ":" + dependencyNode.getArtifact().getArtifactId();
            if(!dependenciesByArtifact.containsKey(artifactKey))
                dependenciesByArtifact.put(artifactKey,new HashSet<String>());
            Set<MavenDependencyNode> dependencyNodeSet;
            if(transitiveDep)
                dependencyNodeSet = dependencyNode.getAllDependency();
            else
                dependencyNodeSet = dependencyNode.getDependency();

            for(MavenDependencyNode subDependencyNode : dependencyNodeSet) {
                String dependencyKey = subDependencyNode.getArtifact().getGroupId() + ":" + subDependencyNode.getArtifact().getArtifactId();

                dependenciesByArtifact.get(artifactKey).add(dependencyKey);
            }
        }
        return dependenciesByArtifact;
    }

    /**
     * compute for each dependency, his number of user
     * @param transitiveDep adds (or not) the transitive dependency
     * @return
     */
    public Map<String,Set<String>> artifactUsedDistribution(boolean transitiveDep) {
        Map<String,Set<String>> dependenciesByArtifact = new HashMap<String, Set<String>>();
        for(MavenDependencyNode dependencyNode  : dependencyGraph.getNodes().values()) {
            String artifactKey =  dependencyNode.getArtifact().getGroupId() + ":" + dependencyNode.getArtifact().getArtifactId();
            Set<MavenDependencyNode> dependencyNodeSet;
            if(transitiveDep)
                dependencyNodeSet = dependencyNode.getAllDependency();
            else
                dependencyNodeSet = dependencyNode.getDependency();
            for(MavenDependencyNode subDependencyNode : dependencyNodeSet) {
                String dependencyKey = subDependencyNode.getArtifact().getGroupId() + ":" + subDependencyNode.getArtifact().getArtifactId();
                if(!dependenciesByArtifact.containsKey(dependencyKey))
                    dependenciesByArtifact.put(dependencyKey,new HashSet<String>());
                dependenciesByArtifact.get(dependencyKey).add(artifactKey);
            }
        }
        return dependenciesByArtifact;
    }

    public void writeNormalizedData(String head, Map<String, Set<String>> data) throws IOException {
        FileWriter fw = new FileWriter((fileName.endsWith("/")?fileName:fileName+"_")+head+"_"+System.currentTimeMillis() + ".csv");
        BufferedWriter bw = new BufferedWriter(fw);
        bw.write(head+"\n");
        Log.debug(head);
        double[] normalize = normalize(data);
        if(normalize != null) {
            for (int i = 0; i < normalize.length; i++) {
                bw.write(normalize[i] + "\n");
            }
        }
        bw.close();
    }

    public void writeData(String head, Map<String, Set<String>> data) throws IOException {
        FileWriter fw = new FileWriter((fileName.endsWith("/")?fileName:fileName+"_")+head+"_"+System.currentTimeMillis() + ".csv");
        BufferedWriter bw = new BufferedWriter(fw);
        bw.write(head+"\n");
        Log.debug(head);
        for(String key : data.keySet()) {
            bw.write(key + "," + data.get(key).size() + ",");
            for(String value : data.get(key)) {
                bw.write(value + ",");
            }
            bw.write(System.getProperty("line.separator"));
        }
        bw.close();
    }

    protected double[] normalize(Map<String,Set<String>> map) {
        if(map == null)
            return null;
        if(map.size() == 0)
            return null;
        int size = map.size();
        double[] data = new double[size];
        int j = 0;
        for(Set<String> set : map.values()) {
            data[j] = set.size();
            j++;
        }

//        Integer[] data = map.values().toArray(new Integer[size]);
//        double sum = 0;
        Arrays.sort(data);
        double maxValue = data[size - 1];
        Log.debug("{} {}",data[0],data[size - 1]);
        double[] ret = new double[100];
        double p = size/100;
        for (int i = 0; i < 100; i++) {
            ret[i] = ((double)data[(int)(p*i)]);
//            sum = sum + ret[i];
        }
        Log.debug("size {}",size);
        return data;
    }
}
