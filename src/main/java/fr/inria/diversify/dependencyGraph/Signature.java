package fr.inria.diversify.dependencyGraph;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * User: Simon
 * Date: 6/24/13
 * Time: 9:59 AM
 */
public class Signature {
    String name;
    String className;
    String returnType;
    String param;

    public Signature(String className, String name, String param, String returnType) {
        this.className = className;
        this.name = name;
        this.param = param;
        this.returnType = returnType;
    }

    public Signature(String signature) {
        Pattern pattern = Pattern.compile( 	"((\\w+\\/)*\\w+)\\.(\\w+):\\(([^\\)]*)\\)((\\w+\\/)*\\w+;?)");
        Matcher m = pattern.matcher(signature);
       if(m.matches()) {
        className = m.group(1);
        name = m.group(2);
        param = m.group(4);
        returnType = m.group(5);
       }
       else {
           className = "";
           name = "";
           param = "";
           returnType = "";
       }

    }

//    "javax/xml/stream/XMLStreamWriter.writeCharacters:(Ljava/lang/String;)V","javax/xml/stream/XMLStreamWriter.writeDefaultNamespace:(Ljava/lang/String;)V"
    @Override
    public String toString() {
        return className+"."+name+"("+param+")"+returnType;
    }

    public String toJSON() {
        return MavenDependencyGraph.stringToId(className) + ":" +
                MavenDependencyGraph.stringToId(name) + ":" +
                MavenDependencyGraph.stringToId(param)+ ":" +
                MavenDependencyGraph.stringToId(returnType);
    }

    @Override
    public boolean equals(Object o) {
        if(!(o instanceof Signature))
            return  false;
        Signature s = (Signature)o;
        return name.equals(s.name) && className.equals(s.className) && param.equals(s.param) && returnType.equals(s.returnType);
    }


    @Override
    public int hashCode()
    {
        int hash = 17;
        hash = hash * 31 + name.hashCode();
        hash = hash * 31 + className.hashCode();
        hash = hash * 31 + param.hashCode();
        hash = hash * 31 + returnType.hashCode();
        return hash;
    }
}


