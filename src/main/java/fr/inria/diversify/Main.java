package fr.inria.diversify;

import fr.inria.diversify.dependencyGraph.MavenDependencyGraph;
import fr.inria.diversify.dependencyGraph.GraphBuilder;
import fr.inria.diversify.dependencyGraph.MavenDependencyNode;
import fr.inria.diversify.maven.CentralIndex;
import fr.inria.diversify.stat.Stat;
import fr.inria.diversify.stat.Stat2;
import fr.inria.diversify.util.Log;
import org.apache.maven.index.ArtifactInfo;
import org.apache.maven.index.artifact.Gav;

import java.io.*;
import java.util.*;

/**
 * User: Simon
 * Date: 6/13/13
 * Time: 10:23 AM
 */
public class Main {

    public static boolean mergingVersions = true;
    static long totalArtifactsNumber;

    public static void main( String[] args ) throws Exception
    {
        Log.set(Log.LEVEL_DEBUG);

        Log.info("Building artifact index");
        writeAllArtifactInfo("allArtifact");
        compactArtifacts("allArtifact", "allArtifactCompact");

        Log.info("Starting dependency graph build");
        MavenDependencyGraph dependencyGraph;
        if(args.length < 1) {
            //Log.info("number of artifact: {}",allArtifact("allArtifact").size());
            //dependencyGraph = (new GraphBuilder()).buildGraphDependency(allArtifact("allArtifact"));
            //dependencyGraph.toJSONObjectIn("mavenGraph_" + System.currentTimeMillis() + ".json");
            int subListsNumber = 10;
            for(int index = 0; index < subListsNumber; index++) {
                dependencyGraph = (new GraphBuilder()).buildGraphDependency(allArtifact("allArtifact").subList((int)(((long)(totalArtifactsNumber * index)) / subListsNumber), Math.min((int)(((long)(totalArtifactsNumber * (index + 1))) / subListsNumber), (int)totalArtifactsNumber)));
                dependencyGraph.toJSONObjectIn("mavenGraph_" + System.currentTimeMillis() + ".json");
                Log.info("Starting POM files analysis");
                Stat2 stat2 = new Stat2(dependencyGraph,"resultCSV/");
                stat2.writeGeneralStat(String.valueOf(index));
                Log.info(dependencyGraph.info());
            }
        }
        else if(args.length < 2) {
            dependencyGraph = (new GraphBuilder()).forJSONFiles(args[0]);
            Log.info("Starting POM files analysis");
            Stat2 stat = new Stat2(dependencyGraph,"resultCSV/");
            stat.writeGeneralStat("full");
            Log.info(dependencyGraph.info());
        }
        else {
            int bMin = Integer.parseInt(args[0]);
            int bMax = Integer.parseInt(args[1]);
            Log.info("number of artifact: {}",allArtifact("allArtifact").size());
            dependencyGraph = (new GraphBuilder()).buildGraphDependency(allArtifact("allArtifact").subList(bMin, bMax));
            dependencyGraph.toJSONObjectIn("mavenGraph_" + System.currentTimeMillis() + ".json");
            Log.info("Starting POM files analysis");
            Stat2 stat = new Stat2(dependencyGraph,"resultCSV/");
            stat.writeGeneralStat(bMin + "-" + bMax);
            Log.info(dependencyGraph.info());
        }
        //renameArtifacts("raw/dependencies.csv", "raw/usages.csv", "raw/renamed_dependencies.csv", "raw/renamed_usages.csv");


        //writeDot(dependencyGraph);
        //writeStat(dependencyGraph);
        //test(dependencyGraph);
        //Log.info("Starting POM files analysis");
        //Stat2 stat = new Stat2(dependencyGraph,"resultCSV/");
        //stat.writeGeneralStat();
        //Log.info(dependencyGraph.info());
    }

    public static void writeAllArtifactInfo(String fileName) {
        try {
            CentralIndex app = new CentralIndex();
            app.buildCentralIndex();
            totalArtifactsNumber = app.allArtifactSize();
            int subListsNumber = 100000;
            int count = 0, error = 0;
            List<ArtifactInfo> partialArtifact;
            FileWriter fw = new FileWriter(fileName);
            BufferedWriter bw = new BufferedWriter(fw);
            Gav gav;
            for(int index = 0; index < subListsNumber; index++) {
                partialArtifact = app.partialArtifactInfo((int)(((long)(totalArtifactsNumber * index)) / subListsNumber),
                        Math.min((int)(((long)(totalArtifactsNumber * (index + 1))) / subListsNumber), (int)totalArtifactsNumber));
                //Log.debug("Storing artifacts from index {} to {}", (totalArtifactsNumber * index) / subListsNumber, Math.min((totalArtifactsNumber * (index + 1)) / subListsNumber, totalArtifactsNumber));
                String text = "";
                for (ArtifactInfo ai : partialArtifact) {
                    try {
                        gav = ai.calculateGav();
                        text += gav.getGroupId() + ":" + gav.getArtifactId() + ":" + gav.getVersion() + System.getProperty("line.separator");
                        count++;
                    } catch (Exception ex) {
                        error++;
                    }
                }
                bw.write(text);
                bw.flush();
            }
            bw.close();

            Log.info("count: {}, error: {}", count, error);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public static void compactArtifacts(String artifactFileName, String outputFileName) throws IOException {
        FileReader fr = new FileReader(artifactFileName);
        BufferedReader br = new BufferedReader(fr);
        Set<String> artifacts = new TreeSet<String>();
        String line;
        while((line = br.readLine()) != null) {
            artifacts.add(line.split(":")[0] + ":" + line.split(":")[1]);
        }
        br.close();
        FileWriter fw = new FileWriter(outputFileName);
        BufferedWriter bw = new BufferedWriter(fw);
        for(String artifact : artifacts) {
            bw.write(artifact + System.getProperty("line.separator"));
        }
        bw.close();
    }

    public static void renameArtifacts(String dependencyTableFileName, String usageTableFileName,
                                       String outputDependencyFileName, String outputUsageFileName) throws IOException {
        //dependencies
        FileReader fr = new FileReader(dependencyTableFileName);
        BufferedReader br = new BufferedReader(fr);
        Map<String, String> servicesById = new HashMap<String, String>();
        String line;
        String[] splitLine;
        int counter = 0;
        while((line = br.readLine()) != null) {
            splitLine = line.split(",");
            servicesById.put(splitLine[0], "s" + counter);
            counter++;
            for(int i = 2; i < splitLine.length; i++) {
                if(!servicesById.containsKey(splitLine[i])) {
                    servicesById.put(splitLine[i], "s" + counter);
                    counter++;
                }
            }
        }
        int depCounter = counter;
        Log.info("Found {} dependency nodes", depCounter);
        br.close();
        //usages
        fr = new FileReader(usageTableFileName);
        br = new BufferedReader(fr);
        while((line = br.readLine()) != null) {
            splitLine = line.split(",");
            if(!servicesById.containsKey(splitLine[0])) {
                servicesById.put(splitLine[0], "s" + counter);
                counter++;
            }
            for(int i = 2; i < splitLine.length; i++) {
                if(!servicesById.containsKey(splitLine[i])) {
                    servicesById.put(splitLine[i], "s" + counter);
                    counter++;
                }
            }
        }
        Log.info("Found {} usage nodes", counter - depCounter);
        br.close();
        //rewrite
        fr = new FileReader(dependencyTableFileName);
        br = new BufferedReader(fr);
        FileWriter fw = new FileWriter(outputDependencyFileName);
        BufferedWriter bw = new BufferedWriter(fw);
        while((line = br.readLine()) != null) {
            splitLine = line.split(",");
            bw.write(servicesById.get(splitLine[0]) + "," + splitLine[1] + ",");
            for(int i = 2; i < splitLine.length; i++) {
                bw.write(servicesById.get(splitLine[i]) + ",");
            }
            bw.write(System.getProperty("line.separator"));
        }
        bw.close();
        fr = new FileReader(usageTableFileName);
        br = new BufferedReader(fr);
        fw = new FileWriter(outputUsageFileName);
        bw = new BufferedWriter(fw);
        while((line = br.readLine()) != null) {
            splitLine = line.split(",");
            bw.write(servicesById.get(splitLine[0]) + "," + splitLine[1] + ",");
            for(int i = 2; i < splitLine.length; i++) {
                bw.write(servicesById.get(splitLine[i]) + ",");
            }
            bw.write(System.getProperty("line.separator"));
        }
        bw.close();
    }

    public static void writeDot(MavenDependencyGraph g) throws IOException {
        String dot = g.toDot();
        FileWriter fw = new FileWriter("test.dot");
        BufferedWriter bw = new BufferedWriter(fw);
        bw.write(dot);
        bw.close();
    }

    public static List<String> allArtifact(String fileName) throws IOException {
        List<String> artifacts = new ArrayList<String>();
        File f  = new File(fileName);
        BufferedReader br = new BufferedReader(new FileReader(f));
        String line = br.readLine();
        while (line != null) {
            artifacts.add(line);
            line = br.readLine();
        }
        return  artifacts;
    }

   public static void writeStat(MavenDependencyGraph g) throws IOException {
       Stat stat = new Stat(g);

       stat.writeGeneralStat("stat");
       stat.writeDependencyStat("org.jvnet.hudson.main", "hudson-core");
       stat.writeDependencyStat("org.mortbay.jetty", "jsp-2.0");
       stat.writeDependencyStat("org.mule", "mule-core");
       stat.writeDependencyStat("org.ow2.jonas", "jonas-services-api");
       stat.writeDependencyStat("dom4j", "dom4j");
       stat.writeDependencyStat("commons-logging", "commons-logging");
   }

    public  static void test(MavenDependencyGraph g) throws IOException {
        Map<String, Set<MavenDependencyNode>> tmp = g.dependencyUsedDistribution("org.objectweb.fractal", "fractal-api", false);

        HashSet<String> tmp2 = new HashSet<String>();
        for(String key : tmp.keySet())
            for (MavenDependencyNode node : tmp.get(key))
                tmp2.add(node.toString());

        FileWriter fw = new FileWriter("test");
        BufferedWriter bw = new BufferedWriter(fw);
        for(String s: tmp2)
            fw.write(s+"\n");
        bw.close();
    }
}
