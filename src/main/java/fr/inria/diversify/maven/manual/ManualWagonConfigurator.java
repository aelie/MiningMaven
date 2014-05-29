package fr.inria.diversify.maven.manual;

import org.apache.maven.wagon.Wagon;
import org.eclipse.aether.connector.wagon.WagonConfigurator;

/**
 * User: Simon
 * Date: 6/12/13
 * Time: 3:44 PM
 */
public class ManualWagonConfigurator  implements WagonConfigurator
{

    public void configure( Wagon wagon, Object configuration )
            throws Exception
    {
        // no-op
    }

}
