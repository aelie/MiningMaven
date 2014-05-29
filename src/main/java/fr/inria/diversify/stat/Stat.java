package fr.inria.diversify.stat;

import fr.inria.diversify.dependencyGraph.MavenDependencyGraph;
import fr.inria.diversify.dependencyGraph.MavenDependencyNode;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

/**
 * User: Simon
 * Date: 7/2/13
 * Time: 12:30 PM
 */
public class Stat {
    MavenDependencyGraph g;

    public Stat(MavenDependencyGraph g) {
        this.g = g;
    }

    public void writeGeneralStat(String fileName) throws IOException {
//        Map<String, Integer> dd = dependencyGraph.artifactDependencyDistribution(false);
//        Map<String, Integer> alldd = dependencyGraph.artifactDependencyDistribution(true);
//        Map<String, Integer> du = dependencyGraph.artifactUsedDistribution(false);
//        Map<String, Integer> alldu = dependencyGraph.artifactUsedDistribution(true);
//        Map<String, Integer> ad = dependencyGraph.artifactDistribution();
//
//        FileWriter fw = new FileWriter(fileName+"_artifactDistribution.csv");
//        BufferedWriter bw = new BufferedWriter(fw);
//        bw.writeNormalizedData("Artifact;number;directDependency;allDependency;directUsed;allUsed\n");
//        for (String s : dd.keySet()) {
//
//            bw.writeNormalizedData(s+";"+ad.get(s)+";"+((double)dd.get(s)/(double)ad.get(s))+";"+((double)alldd.get(s)/(double)ad.get(s)));
//            if(du.containsKey(s))
//                bw.writeNormalizedData(";"+((double)du.get(s)/(double)ad.get(s))+";"+((double)alldu.get(s)/(double)ad.get(s))+"\n");
//            else
//                bw.writeNormalizedData(";0;0\n");
//        }
//
//        bw.close();
    }

    public void writeDependencyStat(String groupId, String artifactId) throws IOException {
        Map<String,Set<MavenDependencyNode>> dd = g.dependencyDistribution(groupId, artifactId, false);
        Map<String,Set<MavenDependencyNode>> alldd = g.dependencyDistribution(groupId, artifactId, true);
        Map<String,Set<MavenDependencyNode>> du = g.dependencyUsedDistribution(groupId, artifactId, false);
        Map<String,Set<MavenDependencyNode>> alldu = g.dependencyUsedDistribution(groupId, artifactId, true);

        Set<MavenDependencyNode> oldDd = new HashSet<MavenDependencyNode>();
        Set<MavenDependencyNode> oldAllDd = new HashSet<MavenDependencyNode>();
        Set<MavenDependencyNode> oldDu = new HashSet<MavenDependencyNode>();
        Set<MavenDependencyNode> oldAllDu = new HashSet<MavenDependencyNode>();

        FileWriter fw = new FileWriter(artifactId+"_dependency.csv");
        BufferedWriter bw = new BufferedWriter(fw);

        bw.write("version;directDependency;allDependency;directDependencyNew;directDependencyRemove;directDependencySame;" +
                "allDependencyNew;allDependencyRemove;allDependencySame;directUsed;allUsed;directUsedNew;directUsedRemove;directUsedSame;" +
                "allUsedNew;allUsedRemove;allUsedSame\n");
        for (String i : sortVersion(dd.keySet())){
            bw.write(i+";"+dd.get(i).size()+";"+alldd.get(i).size()+";"
                    +newDep(oldDd, dd.get(i))+";"+removeDep(oldDd, dd.get(i))+";"+sameDep(oldDd, dd.get(i))+
                    ";"+newDep(oldAllDd, alldd.get(i))+";"+removeDep(oldAllDd, alldd.get(i))+";"+sameDep(oldAllDd, alldd.get(i)));
            if(du.containsKey(i)) {
                bw.write(";"+du.get(i).size()+";"+alldu.get(i).size()+";"
                        +newDep(oldDu, du.get(i))+";"+removeDep(oldDu, du.get(i))+";"+sameDep(oldDu, du.get(i))+
                        ";"+newDep(oldAllDu, alldu.get(i))+";"+removeDep(oldAllDu, alldu.get(i))+";"+sameDep(oldAllDu, alldu.get(i))+"\n");
                oldDu = du.get(i);
                oldAllDu = alldu.get(i);
            }
            else {
                bw.write(";0;0;0;0;0;0;0;\n");
                oldDu.clear();
                oldAllDu.clear();
            }
            oldDd = dd.get(i);
            oldAllDd = alldd.get(i);
        }
        bw.close();
    }

    public int newDep(Set<MavenDependencyNode> oldSet, Set<MavenDependencyNode> newSet) {
        int count = 0;
        for (MavenDependencyNode n : newSet)
            if(!oldSet.contains(n))
                count++;
        return count;
    }

    public int removeDep(Set<MavenDependencyNode> oldSet, Set<MavenDependencyNode> newSet) {
        int count = 0;
        for (MavenDependencyNode n : oldSet)
            if(!newSet.contains(n))
                count++;
        return count;
    }

    public int sameDep(Set<MavenDependencyNode> oldSet, Set<MavenDependencyNode> newSet) {
        int count = 0;
        for (MavenDependencyNode n : oldSet)
            if(newSet.contains(n))
                count++;
        return count;
    }

    protected List<String> sortVersion(Collection<String> version) {
        List<String> l = new ArrayList<String>();
        l.addAll(version);
        Collections.sort(l, new Comparator<String>() {
            @Override
            public int compare(String s, String s2) {
                String[] tmp1 = s.split("-");
                String[] tmp2 = s2.split("-");

                int c = tmp1[0].compareTo(tmp2[0]);

                if (c != 0)
                    return c;
                else
                if(tmp1.length > 1) {
                    if(tmp2.length > 1)
                        return tmp1[1].compareTo(tmp2[1]);
                    else
                        return -1;
                } else {
                    if(tmp2.length > 1)
                        return 1;
                    else
                        return 0;
                }
            }
        });
        return l;
    }
}
