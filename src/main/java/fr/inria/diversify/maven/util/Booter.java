package fr.inria.diversify.maven.util;

import fr.inria.diversify.maven.manual.ManualRepositorySystemFactory;
import fr.inria.diversify.maven.ConsoleTransferListener;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Booter {
    static RepositorySystem system;


    public static RepositorySystem newRepositorySystem()
    {
        if(system ==null)
            system  = ManualRepositorySystemFactory.newRepositorySystem();
        // return org.eclipse.aether.examples.guice.GuiceRepositorySystemFactory.newRepositorySystem();
        // return org.eclipse.aether.examples.plexus.PlexusRepositorySystemFactory.newRepositorySystem();
        return system;
    }

    public static DefaultRepositorySystemSession newRepositorySystemSession( RepositorySystem system )
    {
        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();

        LocalRepository localRepo = new LocalRepository( "target/local-repo" );
        session.setLocalRepositoryManager( system.newLocalRepositoryManager( session, localRepo ) );

        session.setTransferListener( new ConsoleTransferListener() );
        session.setRepositoryListener( new LogRepositoryListener() );

        // uncomment to generate dirty trees
        // session.setDependencyGraphTransformer( null );

        return session;
    }

    public static List<RemoteRepository> newRepositories(RepositorySystem system, RepositorySystemSession session) {
        return new ArrayList<RemoteRepository>(Arrays.asList(newCentralRepository()));
    }

    public static RemoteRepository newCentralRepository()
    {
        return new RemoteRepository.Builder( "central", "default", "http://repo1.maven.org/maven2/" ).build();
    }
}
