package fr.inria.diversify.parser;

import org.apache.commons.io.FileUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * User: Simon
 * Date: 6/18/13
 * Time: 11:52 AM
 */
public class JarParser {
    protected Set<String> publicMethods;
    protected Set<String> calls;
    protected String jar;

    public JarParser(String jar) {
        this.jar = jar;
        publicMethods = new HashSet<String>();
        calls = new HashSet<String>();
    }

    public void parse() throws IOException, InterruptedException {
        System.out.println("explore jar: " + jar);
        String tmpDir = prepare();

        Set<String> jf = allJavaFile(new File(tmpDir), new File(tmpDir));
        StringBuffer listClass = new StringBuffer();
        for(String key : jf)
            listClass.append(key+ " ");

        parsePublicMethod(tmpDir, listClass.toString());
        parseCall(tmpDir,listClass.toString());

//        FileUtils.deleteDirectory(new File(tmpDir));
    }


    protected Set<String> allJavaFile(File CurrentDir, File rootDir) {
        Set<String> list = new HashSet<String>();
        for (File file : CurrentDir.listFiles())
            if (file.isFile()) {
                if (file.getName().endsWith(".class")) {
                    String className = file.getAbsolutePath().replace(".class","");
                    className = className.replace(rootDir.getAbsolutePath()+"/", "");
                    list.add(className);
                }
            } else
                list.addAll(allJavaFile(file,rootDir));
        return list;
    }

    protected String prepare() throws IOException, InterruptedException {
        File tmpDir = new File("tmpDir");
        if(tmpDir.exists())
            FileUtils.deleteDirectory(tmpDir);

        tmpDir.mkdir();

        Runtime r = Runtime.getRuntime();
        Process p = new ProcessBuilder("sh", "unjar.sh",tmpDir.getAbsolutePath(), jar).start();
        p.waitFor();
        return tmpDir.getAbsolutePath();
    }

    public void parsePublicMethod(String directory,String classFileName) {
        String output = runJavap("-public -s", directory, classFileName);

        String className = null;
        String tmp[] = output.split("\n");

        for (int i = 0; i < tmp.length - 1; i++) {
          String cl = parseClassDeclaration(tmp[i]);
            if(cl != null)
                className = cl;
            if (tmp[i].contains("(") && tmp[i + 1].contains("Signature:"))
                try {
                    publicMethods.add(parseCall(tmp[i], className, tmp[i + 1]));
                }  catch (Exception e) {}

        }
    }

    protected String parseClassDeclaration(String line) {
        Pattern pattern = Pattern.compile("((public|private|static|final|protected|abstract)\\s)+(class|interface)\\s((\\w+\\.)*\\w+)\\s*[^\\{]*\\s*\\{");
        Matcher m = pattern.matcher(line);
        if (m.matches()) {

            return m.group(m.groupCount()-1);
        }
        else
            return null;
    }

    public void parseCall(String directory,String classFileName) {
        String output = runJavap("-c", directory, classFileName);
        for(String line: output.split("\n")) {
            if(line.contains("invokevirtual") |
                    line.contains("invokespecial") |
                    line.contains("invokeinterface") |
                    line.contains("invokestatic")){
                String tmp[] = line.split(" ");
                calls.add(tmp[tmp.length-1]);
            }
        }
    }

    protected String parseCall(String call, String className,  String signature) {
        String result = className.replace(".", "/");
        String tmp[] = call.split("\\)")[0].split(" ");
        result = result + "." +tmp[tmp.length -1].split("\\(")[0];
        tmp = signature.split(" ");
        result = result + ":" +tmp[tmp.length -1];
        return result;
    }

    protected String runJavap(String args, String directory, String classFileName) {
        StringBuffer output = null;
        Runtime r = Runtime.getRuntime();
        try {
            Process p = r.exec("javap " + args+ " -classpath "+directory+" "+classFileName);
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            output = new StringBuffer();
            while ((line = reader.readLine()) != null) {
                output.append(line + "\n");
            }
            reader.close();

        } catch (Exception e) {}
        return output.toString();
    }


    public Set<String> getCalls() {
        return calls;
    }
    public Set<String> getPublicMethods() {
        return publicMethods;
    }


}
