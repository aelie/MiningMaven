package fr.inria.diversify.maven.util;

import fr.inria.diversify.util.Log;
import org.eclipse.aether.AbstractRepositoryListener;
import org.eclipse.aether.RepositoryEvent;

import java.io.PrintStream;

/**
 * User: Simon
 * Date: 6/12/13
 * Time: 3:46 PM
 */
public class LogRepositoryListener extends AbstractRepositoryListener
{


    public void artifactDeployed( RepositoryEvent event )
    {
        Log.debug("Deployed {} to {}", event.getArtifact(),event.getRepository());
    }

    public void artifactDeploying( RepositoryEvent event )
    {
        Log.debug( "Deploying {} to {}", event.getArtifact(), event.getRepository() );
    }

    public void artifactDescriptorInvalid( RepositoryEvent event )
    {
        Log.debug( "Invalid artifact descriptor for {}: {} ", event.getArtifact()
                , event.getException().getMessage() );
    }

    public void artifactDescriptorMissing( RepositoryEvent event )
    {
        Log.debug( "Missing artifact descriptor for {}", event.getArtifact() );
    }

    public void artifactInstalled( RepositoryEvent event )
    {
        Log.debug( "Installed {} to {}", event.getArtifact(), event.getFile() );
    }

    public void artifactInstalling( RepositoryEvent event )
    {
        Log.debug( "Installing {} to {}", event.getArtifact(), event.getFile() );
    }

    public void artifactResolved( RepositoryEvent event )
    {
        Log.debug( "Resolved artifact {} from {}", event.getArtifact(), event.getRepository() );
    }

    public void artifactDownloading( RepositoryEvent event )
    {
        Log.debug( "Downloading artifact {} from {}", event.getArtifact(), event.getRepository() );
    }

    public void artifactDownloaded( RepositoryEvent event )
    {
        Log.debug( "Downloaded artifact {} from {}", event.getArtifact(), event.getRepository() );
    }

    public void artifactResolving( RepositoryEvent event )
    {
        Log.debug( "Resolving artifact {}", event.getArtifact() );
    }

    public void metadataDeployed( RepositoryEvent event )
    {
        Log.debug( "Deployed {} to {}", event.getMetadata(), event.getRepository() );
    }

    public void metadataDeploying( RepositoryEvent event )
    {
        Log.debug( "Deploying {} to {}", event.getMetadata(), event.getRepository() );
    }

    public void metadataInstalled( RepositoryEvent event )
    {
        Log.debug( "Installed {} to {}" + event.getMetadata(), event.getFile() );
    }

    public void metadataInstalling( RepositoryEvent event )
    {
        Log.debug( "Installing {} to {}", event.getMetadata(), event.getFile() );
    }

    public void metadataInvalid( RepositoryEvent event )
    {
        Log.debug( "Invalid metadata {}", event.getMetadata() );
    }

    public void metadataResolved( RepositoryEvent event )
    {
        Log.debug( "Resolved metadata {} from {}", event.getMetadata(), event.getRepository() );
    }

    public void metadataResolving( RepositoryEvent event )
    {
        Log.debug( "Resolving metadata {} from {}", event.getMetadata(), event.getRepository() );
    }

}

