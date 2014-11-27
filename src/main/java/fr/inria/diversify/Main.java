package fr.inria.diversify;

import fr.inria.diversify.dependencyGraph.GraphBuilder;
import fr.inria.diversify.dependencyGraph.MavenDependencyGraph;
import fr.inria.diversify.dependencyGraph.MavenDependencyNode;
import fr.inria.diversify.maven.CentralIndex;
import fr.inria.diversify.util.Log;
import org.apache.maven.index.ArtifactInfo;
import org.apache.maven.index.artifact.Gav;
import org.codehaus.plexus.util.StringUtils;
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
    static String currentDateAsString;

    static String SEPARATOR_ELEMENTS = ";";
    static String SEPARATOR_ARTIFACTS = ":";

    public static void main(String[] args) throws Exception {
        int parsedStartIndex = -1;
        int parsedEndIndex = -1;
        boolean total = false;
        boolean getUsages = false;
        String usageDate = null;
        String dependencyFileName = null;
        boolean rebuild = true;
        currentDateAsString = timestampToStringDate(System.currentTimeMillis(), true);
        for (int i = 0; i < args.length; i++) {
            if (args[i].equalsIgnoreCase("-help")) {
                Log.info("-start value       start index of the artifact list for dependency calculation" + System.getProperty("line.separator")
                        + "-end value         end index of the artifact list for dependency calculation" + System.getProperty("line.separator")
                        + "-total             calculate all dependencies" + System.getProperty("line.separator")
                        + "-usage [YYYYMMDD]  calculate artifact usages at the specified date" + System.getProperty("line.separator")
                        + "-nobuild           skip artifact index build");
            }
            if (args[i].equalsIgnoreCase("-start") && i < args.length - 1) {
                parsedStartIndex = Integer.parseInt(args[i + 1]);
            }
            if (args[i].equalsIgnoreCase("-end") && i < args.length - 1) {
                parsedEndIndex = Integer.parseInt(args[i + 1]);
            }
            if (args[i].equalsIgnoreCase("-total")) {
                total = true;
            }
            if (args[i].equalsIgnoreCase("-usage")) {
                getUsages = true;
                try {
                    usageDate = args[i + 1];
                    Integer.parseInt(usageDate);
                } catch(Exception ex) {
                    usageDate = null;
                }
            }
            if (args[i].equalsIgnoreCase("-dependency") && i < args.length - 1) {
                dependencyFileName = args[i + 1];
            }
            if (args[i].equalsIgnoreCase("-nobuild")) {
                rebuild = false;
            }
        }
        new File("results/").mkdir();
        Log.set(Log.LEVEL_INFO);
        if (rebuild) {
            Log.info("Building artifact index");
            writeAllArtifactInfo("results/allArtifact");
            Log.info("Compacting artifact index");
            compactArtifacts("results/allArtifact_timestamped", "results/allArtifactCompact");
        }
        //List<String> artifactsMap = allArtifact("results/allArtifact_timestamped");
        Map<String, String> artifactsMap = allArtifactAsMap("results/allArtifact_timestamped");
        Log.info("number of artifact: {}", artifactsMap.size());
        int startIndex = -1;
        int endIndex = -1;
        if(total) {
            startIndex = 0;
            endIndex = artifactsMap.size();
        } else {
            if (parsedStartIndex != -1) {
                if (parsedStartIndex < 0) {
                    Log.info("-start value cannot be " + parsedStartIndex + ", set to 0");
                    startIndex = 0;
                } else {
                    startIndex = parsedStartIndex;
                }
            }
            if (parsedEndIndex != -1) {
                if (parsedEndIndex > artifactsMap.size()) {
                    Log.info("-end value cannot be " + parsedEndIndex + ", set to " + artifactsMap.size());
                    endIndex = artifactsMap.size();
                } else {
                    endIndex = parsedEndIndex;
                }
            }
        }
        if (startIndex != -1 && endIndex != -1) {
            Log.info("Starting dependency graph build");
            buildDependencyFile(artifactsMap, startIndex, endIndex);
        }
        if (getUsages) {
            long timestamp;
            if(usageDate != null) {
                timestamp = stringDateToTimestamp(usageDate);
                Log.info("Starting usage graph build at date " + timestamp);
            } else {
                Log.info("Starting usage graph build at today");
                timestamp = Calendar.getInstance().getTimeInMillis();
            }
            if (dependencyFileName == null) {
                dependencyFileName = "results/directDependencies_" + currentDateAsString + ".csv";
            }
            buildUsagesFile(dependencyFileName, artifactsMap, timestamp);
        }
    }

    public static void buildUsagesFile(String inputFile, Map<String, String> allArtifacts, long timestamp) throws IOException {
        Map<String, Set<String>> usage = new LinkedHashMap<>();
        Map<String, Set<String>> usageNV = new LinkedHashMap<>();
        BufferedReader resultsReader = new BufferedReader(new FileReader(inputFile));
        String line;
        int lineCounter = 0;
        while ((line = resultsReader.readLine()) != null) {
            if (lineCounter++ % 10000 == 0) System.out.println(lineCounter);
            if (!line.equalsIgnoreCase("Artifact" + SEPARATOR_ELEMENTS + "Dependencies")) {
                String[] splitted = line.split(SEPARATOR_ELEMENTS);
                String artifact = splitted[0];
                if(stringDateToTimestamp(allArtifacts.get(artifact)) < timestamp) {
                    List<String> dependencies = new ArrayList<>();
                    List<String> dependenciesNV = new ArrayList<>();
                    if (splitted.length > 1) {
                        dependencies = Arrays.asList(Arrays.copyOfRange(splitted, 2, splitted.length));
                        dependenciesNV = Arrays.asList(Arrays.copyOfRange(splitted, 2, splitted.length)).stream()
                                .map(dependency -> StringUtils.join(Arrays.copyOfRange(dependency.split(SEPARATOR_ARTIFACTS), 0, (dependency.split(SEPARATOR_ARTIFACTS).length - 1)), SEPARATOR_ARTIFACTS))
                                .collect(Collectors.toList());
                    }
                    for (String dependency : dependencies) {
                        if (!usage.containsKey(dependency)) {
                            usage.put(dependency, new HashSet<>());
                        }
                        usage.get(dependency).add(artifact);
                    }
                    for (String dependencyNV : dependenciesNV) {
                        if (dependencyNV.equalsIgnoreCase("")) {
                            System.out.println("ERROR");
                        }
                        if (!usageNV.containsKey(dependencyNV)) {
                            usageNV.put(dependencyNV, new HashSet<>());
                        }
                        usageNV.get(dependencyNV).add(artifact);
                    }
                } else {
                    Log.info("Skipping " + artifact + "(" + allArtifacts.get(artifact) + "), after " + timestampToStringDate(timestamp, false));
                }
            }
        }
        PrintWriter pwResult = new PrintWriter("results/usage_" + currentDateAsString + ".csv", "UTF-8");
        for (String artifact : usage.keySet()) {
            pwResult.print(artifact + SEPARATOR_ELEMENTS + usage.get(artifact).size() + SEPARATOR_ELEMENTS);
            pwResult.println();
            pwResult.flush();
        }
        pwResult.close();
        PrintWriter pwResultNV = new PrintWriter("results/usage_NV_" + currentDateAsString + ".csv", "UTF-8");
        for (String artifactNV : usageNV.keySet()) {
            pwResultNV.print(artifactNV + SEPARATOR_ELEMENTS + usageNV.get(artifactNV).size() + SEPARATOR_ELEMENTS);
            pwResultNV.println();
            pwResultNV.flush();
        }
        pwResultNV.close();
        PrintWriter pwResult_TS = new PrintWriter("results/usage_TS_" + currentDateAsString + ".csv", "UTF-8");
        for (String artifact : usage.keySet()) {
            pwResult_TS.print(artifact + SEPARATOR_ELEMENTS + usage.get(artifact).size() + SEPARATOR_ELEMENTS);
            for(String user : usage.get(artifact)) {
                pwResult_TS.print(user + "@" + allArtifacts.get(user) + SEPARATOR_ELEMENTS);
            }
            pwResult_TS.println();
            pwResult_TS.flush();
        }
        pwResult_TS.close();
        PrintWriter pwResultNV_TS = new PrintWriter("results/usage_NV_TS_" + currentDateAsString + ".csv", "UTF-8");
        for (String artifactNV : usageNV.keySet()) {
            pwResultNV_TS.print(artifactNV + SEPARATOR_ELEMENTS + usageNV.get(artifactNV).size() + SEPARATOR_ELEMENTS);
            for(String user : usageNV.get(artifactNV)) {
                pwResultNV_TS.print(user + "@" + allArtifacts.get(user) + SEPARATOR_ELEMENTS);
            }
            pwResultNV_TS.println();
            pwResultNV_TS.flush();
        }
        pwResultNV_TS.close();
    }

    public static void buildDependencyFile(Map<String, String> allArtifacts, int startIndex, int endIndex) throws FileNotFoundException, UnsupportedEncodingException {
        PrintWriter pw_R = new PrintWriter("results/directDependencies_" + currentDateAsString + ".csv", "UTF-8");
        PrintWriter pw_C = new PrintWriter("counter", "UTF-8");
        pw_R.println("Artifact" + SEPARATOR_ELEMENTS + "Dependencies");
        int counter = startIndex;
        for (String artifactAsString/*WithTS*/ : new ArrayList<>(allArtifacts.keySet()).subList(startIndex, endIndex)) {
            Log.info("" + counter++);
            pw_C.println(counter);
            pw_C.flush();
            //String artifact = StringUtils.join(Arrays.copyOfRange(artifactAsStringWithTS.split(SEPARATOR_ARTIFACTS), 0, 3), SEPARATOR_ARTIFACTS);
            //String timestamp = artifactAsStringWithTS.split(SEPARATOR_ARTIFACTS)[3];
            String artifact = artifactAsString;
            //String timestamp = allArtifacts.get(artifactAsString);
            pw_R.print(artifact + SEPARATOR_ELEMENTS);
            for (Dependency dependency : (new GraphBuilder()).getDirectDependencies(artifact)) {
                String dependencyAsString = dependency.getArtifact().getGroupId() + SEPARATOR_ARTIFACTS
                        + dependency.getArtifact().getArtifactId() + SEPARATOR_ARTIFACTS
                        + dependency.getArtifact().getVersion();
                pw_R.print(dependencyAsString + SEPARATOR_ELEMENTS);
            }
            pw_R.println();
            pw_R.flush();
        }
        pw_R.close();
        pw_C.close();
    }

    public static void writeAllArtifactInfo(String fileName) {
        try {
            CentralIndex centralIndex = new CentralIndex();
            centralIndex.buildCentralIndex();
            totalArtifactsNumber = centralIndex.allArtifactSize();
            int subListsNumber = 100000;
            int count = 0, error = 0;
            List<ArtifactInfo> artifactInfoList;
            BufferedWriter bw = new BufferedWriter(new FileWriter(fileName));
            BufferedWriter bw_ts = new BufferedWriter(new FileWriter(fileName + "_timestamped"));
            Gav gav;
            String groupId;
            String artifactId;
            String version;
            String formattedTime;
            for (int index = 0; index < subListsNumber; index++) {
                artifactInfoList = centralIndex.partialArtifactInfo(
                        (int) (totalArtifactsNumber * index / subListsNumber),
                        Math.min((int) (((long) (totalArtifactsNumber * (index + 1))) / subListsNumber),
                                (int) totalArtifactsNumber));
                //Log.debug("Storing artifacts from index {} to {}", (totalArtifactsNumber * index) / subListsNumber, Math.min((totalArtifactsNumber * (index + 1)) / subListsNumber, totalArtifactsNumber));
                String text = "";
                String text_ts = "";
                for (ArtifactInfo ai : artifactInfoList) {
                    try {
                        gav = ai.calculateGav();
                        formattedTime = timestampToStringDate(ai.lastModified, false);
                        groupId = gav.getGroupId();
                        artifactId = gav.getArtifactId();
                        version = gav.getVersion();
                        text += groupId + SEPARATOR_ARTIFACTS
                                + artifactId + SEPARATOR_ARTIFACTS
                                + version + System.getProperty("line.separator");
                        text_ts += groupId + SEPARATOR_ARTIFACTS
                                + artifactId + SEPARATOR_ARTIFACTS
                                + version + SEPARATOR_ARTIFACTS
                                + formattedTime + System.getProperty("line.separator");
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
                splitLine = line.split(SEPARATOR_ARTIFACTS);
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
            bw.write(artifact + timestampToStringDate(timestampByArtifact.get(artifact), false) + System.getProperty("line.separator"));
        }
        bw.close();
    }

    public static String timestampToStringDate(long timestamp, boolean formatShort) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(timestamp);
        if(formatShort) {
            return "" + cal.get(Calendar.YEAR) +
                    (cal.get(Calendar.MONTH) + 1 <= 9 ? "0" + (cal.get(Calendar.MONTH) + 1) : (cal.get(Calendar.MONTH) + 1)) +
                    (cal.get(Calendar.DAY_OF_MONTH) <= 9 ? "0" + cal.get(Calendar.DAY_OF_MONTH) : cal.get(Calendar.DAY_OF_MONTH));
        } else {
            return (cal.get(Calendar.DAY_OF_MONTH) <= 9 ? "0" + cal.get(Calendar.DAY_OF_MONTH) : cal.get(Calendar.DAY_OF_MONTH)) + "-" +
                    (cal.get(Calendar.MONTH) + 1 <= 9 ? "0" + (cal.get(Calendar.MONTH) + 1) : (cal.get(Calendar.MONTH) + 1)) + "-" +
                    cal.get(Calendar.YEAR) + "T" +
                    (cal.get(Calendar.HOUR_OF_DAY) <= 9 ? "0" + cal.get(Calendar.HOUR_OF_DAY) : cal.get(Calendar.HOUR_OF_DAY)) + "h" +
                    (cal.get(Calendar.MINUTE) <= 9 ? "0" + cal.get(Calendar.MINUTE) : cal.get(Calendar.MINUTE)) + "m" +
                    (cal.get(Calendar.SECOND) <= 9 ? "0" + cal.get(Calendar.SECOND) : cal.get(Calendar.SECOND)) + "s";
        }
    }

    public static long stringDateToTimestamp(String date) {
        Calendar cal = Calendar.getInstance();
        if(date.length() == 8) {
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            cal.set(Calendar.YEAR, Integer.parseInt(date.substring(0, 4)));
            cal.set(Calendar.MONTH, Integer.parseInt(date.substring(4, 6)) - 1);
            cal.set(Calendar.DAY_OF_MONTH, Integer.parseInt(date.substring(6, 8)));
            return cal.getTimeInMillis();
        } else {
            SimpleDateFormat sdf = new SimpleDateFormat("dd'-'MM'-'yyyy'T'HH'h'mm'm'ss's'");
            try {
                cal.setTime(sdf.parse(date));
                return cal.getTimeInMillis();
            } catch (ParseException e) {
                e.printStackTrace();
                return -1;
            }
        }
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
        BufferedReader br = new BufferedReader(new FileReader(new File(fileName)));
        String line;
        while ((line = br.readLine()) != null) {
            artifacts.add(line);
        }
        br.close();
        return artifacts;
    }

    public static Map<String, String> allArtifactAsMap(String fileName) throws IOException {
        Map<String, String> artifacts = new LinkedHashMap<>();
        BufferedReader br = new BufferedReader(new FileReader(new File(fileName)));
        String line;
        while ((line = br.readLine()) != null) {
            String artifact = StringUtils.join(Arrays.copyOfRange(line.split(SEPARATOR_ARTIFACTS), 0, 3), SEPARATOR_ARTIFACTS);
            String timestamp = line.split(SEPARATOR_ARTIFACTS)[3];
            artifacts.put(artifact, timestamp);
        }
        br.close();
        return artifacts;
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



        /*if (args.length == 0) {
            //Log.info("number of artifact: {}",allArtifact("allArtifact").size());
            //dependencyGraph = (new GraphBuilder()).buildGraphDependency(allArtifact("allArtifact"));
            //dependencyGraph.toJSONObjectIn("mavenGraph_" + System.currentTimeMillis() + ".json");
            int subListsNumber = 10;
            for (int index = 0; index < subListsNumber; index++) {
                dependencyGraph = (new GraphBuilder()).buildGraphDependency(allArtifact("results/allArtifact")
                        .subList(
                                (int) (totalArtifactsNumber * index / subListsNumber),
                                Math.min(
                                        (int) (totalArtifactsNumber * (index + 1) / subListsNumber),
                                        (int) totalArtifactsNumber)));
                dependencyGraph.toJSONObjectIn("results/mavenGraph_" + System.currentTimeMillis() + ".json");
                Log.info("Starting POM files analysis");
                Stat2 stat2 = new Stat2(dependencyGraph, "resultCSV/");
                stat2.writeGeneralStat(String.valueOf(index));
                Log.info(dependencyGraph.info());
            }
        } else if (args.length == 1) {
            dependencyGraph = (new GraphBuilder()).forJSONFiles(args[0]);
            Log.info("Starting POM files analysis");
            Stat2 stat = new Stat2(dependencyGraph, "resultCSV/");
            stat.writeGeneralStat("full");
            Log.info(dependencyGraph.info());
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("-usages")) {
                Map<String, Set<String>> usage = new LinkedHashMap<>();
                Map<String, Set<String>> usageNV = new LinkedHashMap<>();
                BufferedReader resultsReader = new BufferedReader(new FileReader(args[1]));
                String line;
                int lineCounter = 0;
                while ((line = resultsReader.readLine()) != null) {
                    if (lineCounter++ % 10000 == 0) System.out.println(lineCounter);
                    if (!line.equalsIgnoreCase("Artifact" + SEPARATOR_ELEMENTS + "Dependencies")) {
                        String[] splitted = line.split(SEPARATOR_ELEMENTS);
                        String[] splittedNV = Arrays.asList(splitted).stream()
                                .map(artifact -> StringUtils.join(Arrays.copyOfRange(artifact.split(SEPARATOR_ARTIFACTS), 0, (artifact.split(SEPARATOR_ARTIFACTS).length - 1)), SEPARATOR_ARTIFACTS))
                                .toArray(String[]::new);
                        String artifact = splitted[0];
                        String artifactNV = splittedNV[0];
                        List<String> dependencies = new ArrayList<>();
                        List<String> dependenciesNV = new ArrayList<>();
                        if (splitted.length > 1) {
                            dependencies = Arrays.asList(Arrays.copyOfRange(splitted, 1, splitted.length));
                            dependenciesNV = Arrays.asList(Arrays.copyOfRange(splittedNV, 1, splittedNV.length));
                        }
                        for (String dependency : dependencies) {
                            if (!usage.containsKey(dependency)) {
                                usage.put(dependency, new HashSet<>());
                            }
                            usage.get(dependency).add(artifact);
                        }
                        for (String dependencyNV : dependenciesNV) {
                            if (dependencyNV.equalsIgnoreCase("")) {
                                System.out.println(line);
                            }
                            if (!usageNV.containsKey(dependencyNV)) {
                                usageNV.put(dependencyNV, new HashSet<>());
                            }
                            usageNV.get(dependencyNV).add(artifactNV);
                        }
                    }
                }
                PrintWriter pwResult = new PrintWriter("results/usage_" + System.currentTimeMillis() + ".csv", "UTF-8");
                for (String artifact : usage.keySet()) {
                    if (usage.get(artifact).size() > 100) {
                        pwResult.print(artifact + SEPARATOR_ELEMENTS + usage.get(artifact).size() + SEPARATOR_ELEMENTS);
                        for (String dependency : usage.get(artifact)) {
                            pwResult.print(dependency + " ");
                        }
                        pwResult.println();
                        pwResult.flush();
                    }
                }
                pwResult.close();
                PrintWriter pwResultNV = new PrintWriter("results/usage_NV_" + System.currentTimeMillis() + ".csv", "UTF-8");
                for (String artifactNV : usageNV.keySet()) {
                    if (usageNV.get(artifactNV).size() > 100) {
                        pwResultNV.print(artifactNV + SEPARATOR_ELEMENTS + usageNV.get(artifactNV).size() + SEPARATOR_ELEMENTS);
                        for (String dependencyNV : usageNV.get(artifactNV)) {
                            pwResultNV.print(dependencyNV + " ");
                        }
                        pwResultNV.println();
                        pwResultNV.flush();
                    }
                }
                pwResultNV.close();
            } else {
                int bMin = Integer.parseInt(args[0]);
                int bMax = Integer.parseInt(args[1]);
                PrintWriter pw_R = new PrintWriter("results/directDependencies.csv", "UTF-8");
                PrintWriter pw_C = new PrintWriter("counter", "UTF-8");
                pw_R.println("Artifact" + SEPARATOR_ELEMENTS + "Timestamp" + SEPARATOR_ELEMENTS + "Dependencies");
                int counter = bMin;
                for (String artifactAsStringWithTS : allArtifacts.subList(bMin, bMax)) {
                    Log.info("" + counter++);
                    pw_C.println(counter);
                    pw_C.flush();
                    String artifact = StringUtils.join(Arrays.copyOfRange(artifactAsStringWithTS.split(SEPARATOR_ARTIFACTS), 0, 3), SEPARATOR_ARTIFACTS);
                    String timestamp = artifactAsStringWithTS.split(SEPARATOR_ARTIFACTS)[3];
                    pw_R.print(artifact + SEPARATOR_ELEMENTS + timestamp + SEPARATOR_ELEMENTS);
                    for (Dependency dependency : (new GraphBuilder()).getDirectDependencies(artifact)) {
                        String dependencyAsString = dependency.getArtifact().getGroupId() + SEPARATOR_ARTIFACTS
                                + dependency.getArtifact().getArtifactId() + SEPARATOR_ARTIFACTS
                                + dependency.getArtifact().getVersion();
                        pw_R.print(dependencyAsString + SEPARATOR_ELEMENTS);
                    }
                    pw_R.println();
                    pw_R.flush();
                }
                pw_R.close();
                pw_C.close();
            }
        }*/
}
