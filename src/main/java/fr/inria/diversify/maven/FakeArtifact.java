package fr.inria.diversify.maven;

import org.eclipse.aether.artifact.AbstractArtifact;

import java.io.File;
import java.util.Map;

/**
 * User: Simon
 * Date: 6/25/13
 * Time: 11:06 AM
 */
public class FakeArtifact extends AbstractArtifact {
    private String groupId;
    private String artifactId;
    private String version;


    public FakeArtifact(String groupId, String artifactId, String version) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
    }

    public FakeArtifact(String artifact) {
        String[] tmp = artifact.split(":");
        this.groupId = tmp[0];
        this.artifactId = tmp[1];
        this.version = tmp[2];
    }

    @Override
    public String getGroupId() {
        return groupId;
    }

    @Override
    public String getArtifactId() {
        return  artifactId;
    }

    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public String getClassifier() {
        return "";
    }

    @Override
    public String getExtension() {
        return "";
    }

    @Override
    public File getFile() {
        return null;
    }

    @Override
    public Map<String, String> getProperties() {
        return null;
    }


}
