package fr.inria.diversify.maven.util;

import fr.inria.diversify.util.Log;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.DependencyVisitor;

import java.io.PrintStream;

/**
 * User: Simon
 * Date: 6/12/13
 * Time: 3:48 PM
 */
public class LogDependencyGraphDumper implements DependencyVisitor
    {


        private String currentIndent = "";

        public LogDependencyGraphDumper()
        {

        }



    public boolean visitEnter( DependencyNode node )
    {
        Log.debug(currentIndent + node);
        if ( currentIndent.length() <= 0 )
        {
            currentIndent = "+- ";
        }
        else
        {
            currentIndent = "|  " + currentIndent;
        }
        return true;
    }

    public boolean visitLeave( DependencyNode node )
    {
        currentIndent = currentIndent.substring( 3, currentIndent.length() );
        return true;
    }

}