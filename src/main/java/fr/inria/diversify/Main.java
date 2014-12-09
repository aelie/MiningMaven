package fr.inria.diversify;

import fr.inria.diversify.dependencyGraph.GraphBuilder;
import fr.inria.diversify.dependencyGraph.MavenDependencyGraph;
import fr.inria.diversify.maven.CentralIndex;
import fr.inria.diversify.util.Log;
import fr.inria.diversify.util.Tools;
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
    static String usageDateAsString;

    static String SEPARATOR_ELEMENTS = ";";
    static String SEPARATOR_ARTIFACTS = ":";

    //static final long weekLength = 1000 * 60 * 60 * 24 * 7;

    static Map<String, String> usageFiles = new LinkedHashMap<>();
    static Map<String, String> usageFilesNV = new LinkedHashMap<>();

    static Set<String> followSet = new LinkedHashSet<>(Arrays.asList("junit:junit", "org.slf4j:slf4j-api", "log4j:log4j", "org.mockito:mockito-all",
            "org.slf4j:slf4j-log4j12", "javax.servlet:servlet-api", "org.testng:testng", "ch.qos.logback:logback-classic",
            "org.easymock:easymock", "commons-io:commons-io", "commons-logging:commons-logging", "com.google.guava:guava",
            "commons-lang:commons-lang", "org.slf4j:jcl-over-slf4j", "org.scala-lang:scala-library", "org.springframework:spring-context",
            "org.easymock:easymockclassextension", "org.osgi:org.osgi.core", "org.slf4j:slf4j-simple", "org.mockito:mockito-core",
            "org.springframework:spring-test", "org.springframework:spring-core", "org.osgi:org.osgi.compendium", "joda-time:joda-time",
            "commons-codec:commons-codec"));

    public static void main(String[] args) throws Exception {
        int parsedStartIndex = -1;
        int parsedEndIndex = -1;
        boolean total = false;
        boolean getUsages = false;
        String usageDate = null;
        int usageWeeks = 1;
        String usageDependencyFileName = null;
        boolean rebuild = true;
        currentDateAsString = Tools.timestampToStringDate(System.currentTimeMillis(), true);
        for (int i = 0; i < args.length; i++) {
            if (args[i].equalsIgnoreCase("-help")) {
                Log.info("-start value               start index of the artifact list for dependency calculation" + System.getProperty("line.separator")
                        + "-end value                 end index of the artifact list for dependency calculation" + System.getProperty("line.separator")
                        + "-total                     calculate all dependencies" + System.getProperty("line.separator")
                        + "-dependency                specify the dependency filename to be used for usage calculation" + System.getProperty("line.separator")
                        + "-usage [YYYYMMDD] [#weeks] calculate artifact usages at the specified date (default: today) and for #weeks weeks before (default: 1)" + System.getProperty("line.separator")
                        + "-nobuild                   skip artifact index build");
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
                    usageWeeks = Integer.parseInt(args[i + 2]);
                } catch (Exception ex) {
                    usageWeeks = 1;
                }
                try {
                    usageDate = args[i + 1];
                    Integer.parseInt(usageDate);
                } catch (Exception ex) {
                    usageDate = null;
                }
            }
            if (args[i].equalsIgnoreCase("-dependency") && i < args.length - 1) {
                usageDependencyFileName = args[i + 1];
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
        } else {
            Log.info("Skipping artifact index build");
        }
        Log.info("Building artifact/timestamp map");
        Map<String, String> artifactsMap = allArtifactAsMap("results/allArtifact_timestamped");
        Log.info("Number of artifacts: " + artifactsMap.size());
        int startIndex = -1;
        int endIndex = -1;
        if (total) {
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
            Log.info("Starting "
                    + (total ? "total " : "")
                    + "dependency graph build"
                    + (total ? "" : " for indices " + startIndex + " & " + endIndex));
            buildDependencyFile(artifactsMap, startIndex, endIndex);
        } else {
            Log.info("Skipping dependency calculation");
        }
        if (getUsages) {
            long timestamp;
            if (usageDependencyFileName == null) {
                usageDependencyFileName = "results/directDependencies_" + currentDateAsString + ".csv";
            }
            for (int i = 0; i < usageWeeks; i++) {
                if (usageDate != null) {
                    timestamp = Tools.stringDateToTimestamp(usageDate);
                } else {
                    timestamp = Calendar.getInstance().getTimeInMillis();
                }
                timestamp = Tools.increaseTimestamp(timestamp, Calendar.WEEK_OF_YEAR, -1);
                usageDateAsString = currentDateAsString + "(" + Tools.timestampToStringDate(timestamp, true) + ")";
                Log.info("Starting usage graph build at date " + Tools.timestampToStringDate(timestamp, false));
                buildUsageFile(usageDependencyFileName, artifactsMap, timestamp);
            }
            if (usageWeeks > 1) {
                Log.info("Merging usage files");
                mergeUsageFiles();
            }
        } else {
            Log.info("Skipping usage calculation");
        }
        Log.info("Done.");
    }

    public static void buildUsageFile(String usageDependencyFileName, Map<String, String> allArtifacts, long timestamp) throws IOException {
        Map<String, Set<String>> usage = new LinkedHashMap<>();
        Map<String, Set<String>> usageNV = new LinkedHashMap<>();
        BufferedReader resultsReader = new BufferedReader(new FileReader(usageDependencyFileName));
        String line;
        int lineCounter = 0;
        int skippedCounter = 0;
        while ((line = resultsReader.readLine()) != null) {
            if (++lineCounter % 100000 == 0) System.out.println(lineCounter);
            if (!line.equalsIgnoreCase("Artifact" + SEPARATOR_ELEMENTS + "Dependencies")) {
                String[] splitted = line.split(SEPARATOR_ELEMENTS);
                String artifact = splitted[0];
                if (allArtifacts.containsKey(artifact)) {
                    if (Tools.stringDateToTimestamp(allArtifacts.get(artifact)) < timestamp) {
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
                        skippedCounter++;
                        //Log.info("Skipping " + artifact + "(" + allArtifacts.get(artifact) + "), after " + timestampToStringDate(timestamp, false));
                    }
                }
            }
        }
        Log.info("Skipped " + skippedCounter + " artifacts (date)");
        writeUsage(usage, allArtifacts, false);
        writeUsageNV(usageNV, allArtifacts, false);
    }

    public static void writeUsage(Map<String, Set<String>> usage, Map<String, String> allArtifacts, boolean includeTS) throws IOException {
        String filename = "results/usage_" + usageDateAsString + ".csv";
        PrintWriter pwResult = new PrintWriter(filename, "UTF-8");
        for (String artifact : usage.keySet()) {
            pwResult.print(artifact + SEPARATOR_ELEMENTS + usage.get(artifact).size() + SEPARATOR_ELEMENTS);
            pwResult.println();
            pwResult.flush();
        }
        pwResult.close();
        usageFiles.put(usageDateAsString, filename);
        if (includeTS) {
            String filenameTS = "results/usage_TS_" + usageDateAsString + ".csv";
            PrintWriter pwResult_TS = new PrintWriter(filenameTS, "UTF-8");
            for (String artifact : usage.keySet()) {
                pwResult_TS.print(artifact + SEPARATOR_ELEMENTS + usage.get(artifact).size() + SEPARATOR_ELEMENTS);
                for (String user : usage.get(artifact)) {
                    pwResult_TS.print(user + "@" + allArtifacts.get(user) + SEPARATOR_ELEMENTS);
                }
                pwResult_TS.println();
                pwResult_TS.flush();
            }
            pwResult_TS.close();
            usageFiles.put(usageDateAsString, filenameTS);
        }
    }

    public static void writeUsageNV(Map<String, Set<String>> usageNV, Map<String, String> allArtifacts, boolean includeTS) throws IOException {
        String filename = "results/usage_NV_" + usageDateAsString + ".csv";
        PrintWriter pwResultNV = new PrintWriter(filename, "UTF-8");
        for (String artifactNV : usageNV.keySet()) {
            pwResultNV.print(artifactNV + SEPARATOR_ELEMENTS + usageNV.get(artifactNV).size() + SEPARATOR_ELEMENTS);
            pwResultNV.println();
            pwResultNV.flush();
        }
        pwResultNV.close();
        usageFilesNV.put(usageDateAsString, filename);
        if (includeTS) {
            String filenameTS = "results/usage_NV_TS_" + usageDateAsString + ".csv";
            PrintWriter pwResultNV_TS = new PrintWriter(filenameTS, "UTF-8");
            for (String artifactNV : usageNV.keySet()) {
                pwResultNV_TS.print(artifactNV + SEPARATOR_ELEMENTS + usageNV.get(artifactNV).size() + SEPARATOR_ELEMENTS);
                for (String user : usageNV.get(artifactNV)) {
                    pwResultNV_TS.print(user + "@" + allArtifacts.get(user) + SEPARATOR_ELEMENTS);
                }
                pwResultNV_TS.println();
                pwResultNV_TS.flush();
            }
            pwResultNV_TS.close();
            usageFilesNV.put(usageDateAsString, filenameTS);
        }
    }

    public static void mergeUsageFiles() throws IOException {
        BufferedReader resultsReader;
        Map<String, List<String>> weeksByArtifact = new HashMap<>();
        String header = "Artifact" + SEPARATOR_ELEMENTS;
        int usageFileIndex = 0;
        for (String usageDate : usageFiles.keySet()) {
            usageFileIndex++;
            header += usageDate + SEPARATOR_ELEMENTS;
            resultsReader = new BufferedReader(new FileReader(usageFiles.get(usageDate)));
            String line;
            while ((line = resultsReader.readLine()) != null) {
                String artifact = StringUtils.join(
                        Arrays.copyOfRange(line.split(SEPARATOR_ELEMENTS)[0].split(SEPARATOR_ARTIFACTS), 0, 2),
                        SEPARATOR_ARTIFACTS);
                if(followSet.contains(artifact)) {
                    if(!weeksByArtifact.containsKey(artifact)) {
                        weeksByArtifact.put(artifact, new ArrayList<>());
                    }
                    weeksByArtifact.get(artifact).add(line.split(SEPARATOR_ELEMENTS)[1]);
                }
            }
            for(List<String> weekUsages : weeksByArtifact.values()) {
                if(weekUsages.size() < usageFileIndex) {
                    weekUsages.add("0");
                }
            }
        }
        PrintWriter pwResultNV = new PrintWriter("results/merged_usages_" + currentDateAsString + ".csv", "UTF-8");
        pwResultNV.println(header);
        for(String artifact : weeksByArtifact.keySet()) {
            pwResultNV.print(artifact + SEPARATOR_ELEMENTS);
            for(String weeklyValue : weeksByArtifact.get(artifact)) {
                pwResultNV.print(weeklyValue+SEPARATOR_ELEMENTS);
            }
            pwResultNV.println();
            pwResultNV.flush();
        }
        pwResultNV.close();
    }

    public static void buildDependencyFile(Map<String, String> allArtifacts, int startIndex, int endIndex) throws FileNotFoundException, UnsupportedEncodingException {
        PrintWriter pw_R = new PrintWriter("results/directDependencies_" + currentDateAsString + ".csv", "UTF-8");
        PrintWriter pw_C = new PrintWriter("counter", "UTF-8");
        pw_R.println("Artifact" + SEPARATOR_ELEMENTS + "Dependencies");
        int counter = startIndex;
        for (String artifactAsString : new ArrayList<>(allArtifacts.keySet()).subList(startIndex, endIndex)) {
            Log.info("" + counter++);
            pw_C.println(counter);
            pw_C.flush();
            String artifact = artifactAsString;
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
                        formattedTime = Tools.timestampToStringDate(ai.lastModified, false);
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
            bw.write(artifact + Tools.timestampToStringDate(timestampByArtifact.get(artifact), false) + System.getProperty("line.separator"));
        }
        bw.close();
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
        int counter = 0;
        while ((line = br.readLine()) != null) {
            if (++counter % 10000 == 0) System.out.print("+");
            String[] splitted = line.split(SEPARATOR_ARTIFACTS);
            String artifact = StringUtils.join(Arrays.copyOfRange(splitted, 0, splitted.length - 1), SEPARATOR_ARTIFACTS);
            String timestamp = splitted[splitted.length - 1];
            try {
                Tools.stringDateToTimestamp(timestamp);
            } catch (Exception e) {
                e.printStackTrace();
                Log.error("Failed parsing date in " + line);
            }
            artifacts.put(artifact, timestamp);
        }
        System.out.println();
        br.close();
        return artifacts;
    }
}
