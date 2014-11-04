package fr.inria.diversify;

import fr.inria.diversify.dependencyGraph.GraphBuilder;
import fr.inria.diversify.dependencyGraph.MavenDependencyGraph;
import fr.inria.diversify.dependencyGraph.MavenDependencyNode;
import fr.inria.diversify.maven.CentralIndex;
import fr.inria.diversify.stat.Stat;
import fr.inria.diversify.stat.Stat2;
import fr.inria.diversify.util.Log;
import org.apache.maven.index.ArtifactInfo;
import org.apache.maven.index.artifact.Gav;
import org.eclipse.aether.graph.Dependency;

import java.io.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * User: Simon
 * Date: 6/13/13
 * Time: 10:23 AM
 */
public class Main {

    public static boolean mergingVersions = true;
    static long totalArtifactsNumber;

    public static void main(String[] args) throws Exception {
        Log.set(Log.LEVEL_INFO);

        Log.info("Building artifact index");
        //writeAllArtifactInfo("allArtifact");
        Log.info("Compacting artifact index");
        //compactArtifacts("allArtifact_timestamped", "allArtifactCompact");

        Log.info("Starting dependency graph build");
        MavenDependencyGraph dependencyGraph;
        if (args.length < 1) {
            //Log.info("number of artifact: {}",allArtifact("allArtifact").size());
            //dependencyGraph = (new GraphBuilder()).buildGraphDependency(allArtifact("allArtifact"));
            //dependencyGraph.toJSONObjectIn("mavenGraph_" + System.currentTimeMillis() + ".json");
            int subListsNumber = 10;
            for (int index = 0; index < subListsNumber; index++) {
                dependencyGraph = (new GraphBuilder()).buildGraphDependency(allArtifact("allArtifact")
                        .subList(
                                (int) (totalArtifactsNumber * index / subListsNumber),
                                Math.min(
                                        (int) (totalArtifactsNumber * (index + 1) / subListsNumber),
                                        (int) totalArtifactsNumber)));
                dependencyGraph.toJSONObjectIn("mavenGraph_" + System.currentTimeMillis() + ".json");
                Log.info("Starting POM files analysis");
                Stat2 stat2 = new Stat2(dependencyGraph, "resultCSV/");
                stat2.writeGeneralStat(String.valueOf(index));
                Log.info(dependencyGraph.info());
            }
        } else if (args.length < 2) {
            dependencyGraph = (new GraphBuilder()).forJSONFiles(args[0]);
            Log.info("Starting POM files analysis");
            Stat2 stat = new Stat2(dependencyGraph, "resultCSV/");
            stat.writeGeneralStat("full");
            Log.info(dependencyGraph.info());
        } else {
            int bMin = Integer.parseInt(args[0]);
            int bMax = Integer.parseInt(args[1]);
            List<String> allArtifacts = allArtifact("allArtifact");
            Log.info("number of artifact: {}", allArtifacts.size());
            /*dependencyGraph = (new GraphBuilder()).buildGraphDependency(allArtifacts.subList(bMin, bMax));
            System.out.println(allArtifacts.subList(bMin, bMax));
            dependencyGraph.toJSONObjectIn("mavenGraph_" + System.currentTimeMillis() + ".json");
            Log.info("Starting POM files analysis");
            Stat2 stat = new Stat2(dependencyGraph, "resultCSV/");
            stat.writeGeneralStat(bMin + "-" + bMax);
            Log.info(dependencyGraph.info());*/
            PrintWriter pw_R = new PrintWriter("directDependencies.csv", "UTF-8");
            pw_R.println("Artifact,Dependencies");
            int counter = 0;
            for (String artifactAsString : allArtifacts.subList(bMin, bMax)) {
                Log.info("" + counter++);
                pw_R.print(artifactAsString + ",");
                for (Dependency dependency : (new GraphBuilder()).getDirectDependencies(artifactAsString)) {
                    String dependencyAsString = dependency.getArtifact().getGroupId() + ":"
                            + dependency.getArtifact().getArtifactId() + ":"
                            + dependency.getArtifact().getVersion();
                    pw_R.print(dependencyAsString + ",");
                }
                pw_R.println();
                pw_R.flush();
            }
            pw_R.close();
        }
        //renameArtifacts("raw/dependencies.csv", "raw/usages.csv", "raw/renamed_dependencies.csv", "raw/renamed_usages.csv");


        //writeDot(dependencyGraph);
        //writeStat(dependencyGraph);
        //test(dependencyGraph);
        //Log.info("Starting POM files analysis");
        //Stat2 stat = new Stat2(dependencyGraph,"resultCSV/");
        //stat.writeGeneralStat();
        //Log.info(dependencyGraph.info());*/
    }

    public static void writeAllArtifactInfo(String fileName) {
        try {
            CentralIndex app = new CentralIndex();
            app.buildCentralIndex();
            totalArtifactsNumber = app.allArtifactSize();
            int subListsNumber = 100000;
            int count = 0, error = 0;
            List<ArtifactInfo> partialArtifact;
            BufferedWriter bw = new BufferedWriter(new FileWriter(fileName));
            BufferedWriter bw_ts = new BufferedWriter(new FileWriter(fileName + "_timestamped"));
            Gav gav;
            String groupId;
            String artifactId;
            String version;
            String formattedTime;
            for (int index = 0; index < subListsNumber; index++) {
                partialArtifact = app.partialArtifactInfo((int) (totalArtifactsNumber * index / subListsNumber),
                        Math.min((int) (((long) (totalArtifactsNumber * (index + 1))) / subListsNumber), (int) totalArtifactsNumber));
                //Log.debug("Storing artifacts from index {} to {}", (totalArtifactsNumber * index) / subListsNumber, Math.min((totalArtifactsNumber * (index + 1)) / subListsNumber, totalArtifactsNumber));
                String text = "";
                String text_ts = "";
                for (ArtifactInfo ai : partialArtifact) {
                    try {
                        gav = ai.calculateGav();
                        formattedTime = timestampToString(ai.lastModified);
                        groupId = gav.getGroupId();
                        artifactId = gav.getArtifactId();
                        version = gav.getVersion();
                        text += groupId + ":" + artifactId + ":" + version + System.getProperty("line.separator");
                        text_ts += groupId + ":" + artifactId + ":" + version + ":" + formattedTime + System.getProperty("line.separator");
                        count++;
                    } catch (Exception ex) {
                        error++;
                    }
                }
                bw.write(text);
                bw.flush();
                bw_ts.write(text_ts);
                bw_ts.flush();
            }
            bw.close();
            bw_ts.close();

            Log.info("count: {}, error: {}", count, error);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public static void compactArtifacts(String artifactFileName, String outputFileName) throws IOException {
        BufferedReader br = new BufferedReader(new FileReader(artifactFileName));
        Map<String, Long> timestampByArtifact = new LinkedHashMap<>();
        String line;
        String[] splitLine;
        String groupArtifact;
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("dd'-'MM'-'yyyy'T'HH'h'mm'm'ss's'");
        int counter = 1;
        while ((line = br.readLine()) != null) {
            try {
                splitLine = line.split(":");
                groupArtifact = line.substring(0, line.length() - splitLine[splitLine.length - 1].length());
                cal.setTime(sdf.parse(splitLine[splitLine.length - 1]));
                if (timestampByArtifact.keySet().contains(groupArtifact)) {
                    if (cal.getTime().getTime() > timestampByArtifact.get(groupArtifact)) {
                        timestampByArtifact.put(groupArtifact, cal.getTime().getTime());
                    }
                } else {
                    timestampByArtifact.put(groupArtifact, cal.getTime().getTime());
                }
            } catch (NumberFormatException nfe) {
                //System.err.println(line);
            } catch (ParseException e) {
                System.err.println(line + "/" + counter);
                e.printStackTrace();
            }
            counter++;
        }
        br.close();
        BufferedWriter bw = new BufferedWriter(new FileWriter(outputFileName));
        for (String artifact : timestampByArtifact.keySet()) {
            bw.write(artifact + timestampToString(timestampByArtifact.get(artifact)) + System.getProperty("line.separator"));
        }
        bw.close();
    }

    public static String timestampToString(long timestamp) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(timestamp);
        return (cal.get(Calendar.DAY_OF_MONTH) <= 9 ? "0" + cal.get(Calendar.DAY_OF_MONTH) : cal.get(Calendar.DAY_OF_MONTH)) + "-" +
                (cal.get(Calendar.MONTH) + 1 <= 9 ? "0" + (cal.get(Calendar.MONTH) + 1) : (cal.get(Calendar.MONTH) + 1)) + "-" +
                cal.get(Calendar.YEAR) + "T" +
                (cal.get(Calendar.HOUR_OF_DAY) <= 9 ? "0" + cal.get(Calendar.HOUR_OF_DAY) : cal.get(Calendar.HOUR_OF_DAY)) + "h" +
                (cal.get(Calendar.MINUTE) <= 9 ? "0" + cal.get(Calendar.MINUTE) : cal.get(Calendar.MINUTE)) + "m" +
                (cal.get(Calendar.SECOND) <= 9 ? "0" + cal.get(Calendar.SECOND) : cal.get(Calendar.SECOND)) + "s";
    }

    public static void renameArtifacts(String dependencyTableFileName, String usageTableFileName,
                                       String outputDependencyFileName, String outputUsageFileName) throws IOException {
        //dependencies
        FileReader fr = new FileReader(dependencyTableFileName);
        BufferedReader br = new BufferedReader(fr);
        Map<String, String> servicesById = new HashMap<>();
        String line;
        String[] splitLine;
        int counter = 0;
        while ((line = br.readLine()) != null) {
            splitLine = line.split(",");
            servicesById.put(splitLine[0], "s" + counter);
            counter++;
            for (int i = 2; i < splitLine.length; i++) {
                if (!servicesById.containsKey(splitLine[i])) {
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
        while ((line = br.readLine()) != null) {
            splitLine = line.split(",");
            if (!servicesById.containsKey(splitLine[0])) {
                servicesById.put(splitLine[0], "s" + counter);
                counter++;
            }
            for (int i = 2; i < splitLine.length; i++) {
                if (!servicesById.containsKey(splitLine[i])) {
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
        while ((line = br.readLine()) != null) {
            splitLine = line.split(",");
            bw.write(servicesById.get(splitLine[0]) + "," + splitLine[1] + ",");
            for (int i = 2; i < splitLine.length; i++) {
                bw.write(servicesById.get(splitLine[i]) + ",");
            }
            bw.write(System.getProperty("line.separator"));
        }
        bw.close();
        fr = new FileReader(usageTableFileName);
        br = new BufferedReader(fr);
        fw = new FileWriter(outputUsageFileName);
        bw = new BufferedWriter(fw);
        while ((line = br.readLine()) != null) {
            splitLine = line.split(",");
            bw.write(servicesById.get(splitLine[0]) + "," + splitLine[1] + ",");
            for (int i = 2; i < splitLine.length; i++) {
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
        List<String> artifacts = new ArrayList<>();
        File f = new File(fileName);
        BufferedReader br = new BufferedReader(new FileReader(f));
        String line = br.readLine();
        while (line != null) {
            artifacts.add(line);
            line = br.readLine();
        }
        br.close();
        return artifacts;
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

    public static void test(MavenDependencyGraph g) throws IOException {
        Map<String, Set<MavenDependencyNode>> tmp = g.dependencyUsedDistribution("org.objectweb.fractal", "fractal-api", false);
        HashSet<String> tmp2 = new HashSet<>();
        for (String key : tmp.keySet())
            tmp2.addAll(tmp.get(key).stream()
                    .map(MavenDependencyNode::toString)
                    .collect(Collectors.toList()));
        FileWriter fw = new FileWriter("test");
        BufferedWriter bw = new BufferedWriter(fw);
        for (String s : tmp2) {
            fw.write(s + "\n");
        }
        bw.close();
    }
}
