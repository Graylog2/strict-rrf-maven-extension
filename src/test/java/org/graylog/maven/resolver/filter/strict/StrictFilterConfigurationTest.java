package org.graylog.maven.resolver.filter.strict;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.metadata.DefaultMetadata;
import org.eclipse.aether.metadata.Metadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link StrictFilterConfiguration}.
 */
class StrictFilterConfigurationTest {

    @Test
    void testEmptyConfiguration(@TempDir Path tempDir) {
        StrictFilterConfiguration config = StrictFilterConfiguration.load(
                tempDir.toString(),
                tempDir
        );

        assertTrue(config.isEmpty(), "Configuration should be empty when no config files exist");
        assertEquals(tempDir, config.getBasedir(), "Basedir should match");
    }

    @Test
    void testNonExistentDirectory(@TempDir Path tempDir) {
        Path nonExistent = tempDir.resolve("does-not-exist");

        StrictFilterConfiguration config = StrictFilterConfiguration.load(
                nonExistent.toString(),
                tempDir
        );

        assertTrue(config.isEmpty(), "Configuration should be empty when directory doesn't exist");
    }

    @Test
    void testLoadConfiguration(@TempDir Path tempDir) throws Exception {
        // Create config file for "central" repository
        Path configFile = tempDir.resolve("strict-central.txt");
        Files.writeString(configFile,
                "# Comment line\n" +
                        "org.graylog\n" +
                        "org.apache.commons\n" +
                        "\n" +
                        "# Another comment\n" +
                        "org.springframework\n"
        );

        StrictFilterConfiguration config = StrictFilterConfiguration.load(
                tempDir.toString(),
                tempDir
        );

        assertFalse(config.isEmpty(), "Configuration should not be empty");
    }

    @Test
    void testMultipleRepositories(@TempDir Path tempDir) throws Exception {
        // Create config files for multiple repositories
        Files.writeString(tempDir.resolve("strict-central.txt"), "org.graylog\n");
        Files.writeString(tempDir.resolve("strict-company-repo.txt"), "com.company\n");

        StrictFilterConfiguration config = StrictFilterConfiguration.load(
                tempDir.toString(),
                tempDir
        );

        assertFalse(config.isEmpty(), "Configuration should not be empty");
    }

    @Test
    void testArtifactAllowed(@TempDir Path tempDir) throws Exception {
        // Create config file
        Path configFile = tempDir.resolve("strict-central.txt");
        Files.writeString(configFile, "org.graylog\norg.apache.commons\n");

        StrictFilterConfiguration config = StrictFilterConfiguration.load(
                tempDir.toString(),
                tempDir
        );

        // Test exact match
        Artifact artifact1 = new DefaultArtifact("org.graylog:my-artifact:1.0");
        assertTrue(config.isArtifactAllowed("central", artifact1),
                "Artifact with matching groupId should be allowed");

        // Test prefix match
        Artifact artifact2 = new DefaultArtifact("org.graylog.plugin:another-artifact:1.0");
        assertTrue(config.isArtifactAllowed("central", artifact2),
                "Artifact with matching groupId prefix should be allowed");

        // Test not allowed
        Artifact artifact3 = new DefaultArtifact("com.example:different-artifact:1.0");
        assertFalse(config.isArtifactAllowed("central", artifact3),
                "Artifact with non-matching groupId should not be allowed");
    }

    @Test
    void testArtifactAllowedNoConfig(@TempDir Path tempDir) {
        StrictFilterConfiguration config = StrictFilterConfiguration.load(
                tempDir.toString(),
                tempDir
        );

        // When no config exists for a repository, all artifacts should be allowed
        Artifact artifact = new DefaultArtifact("any.group:artifact:1.0");
        assertTrue(config.isArtifactAllowed("central", artifact),
                "Artifact should be allowed when no config exists for repository");
    }

    @Test
    void testMetadataAllowed(@TempDir Path tempDir) throws Exception {
        Path configFile = tempDir.resolve("strict-central.txt");
        Files.writeString(configFile, "org.graylog\n");

        StrictFilterConfiguration config = StrictFilterConfiguration.load(
                tempDir.toString(),
                tempDir
        );

        // Test with groupId
        Metadata metadata1 = new DefaultMetadata("org.graylog", "maven-metadata.xml",
                Metadata.Nature.RELEASE_OR_SNAPSHOT);
        assertTrue(config.isMetadataAllowed("central", metadata1),
                "Metadata with matching groupId should be allowed");

        // Test without groupId (repository-level metadata)
        Metadata metadata2 = new DefaultMetadata("maven-metadata.xml", Metadata.Nature.RELEASE_OR_SNAPSHOT);
        assertTrue(config.isMetadataAllowed("central", metadata2),
                "Metadata without groupId should be allowed");

        // Test with non-matching groupId
        Metadata metadata3 = new DefaultMetadata("com.example", "maven-metadata.xml",
                Metadata.Nature.RELEASE_OR_SNAPSHOT);
        assertFalse(config.isMetadataAllowed("central", metadata3),
                "Metadata with non-matching groupId should not be allowed");
    }

    @Test
    void testRelativeBasedir(@TempDir Path tempDir) throws Exception {
        // Create a subdirectory
        Path subdir = tempDir.resolve(".remoteRepositoryFilters/strict");
        Files.createDirectories(subdir);
        Files.writeString(subdir.resolve("strict-central.txt"), "org.graylog\n");

        // Load with relative path
        StrictFilterConfiguration config = StrictFilterConfiguration.load(
                ".remoteRepositoryFilters/strict",
                tempDir
        );

        assertFalse(config.isEmpty(), "Configuration should load from relative path");
    }

    @Test
    void testAbsoluteBasedir(@TempDir Path tempDir) throws Exception {
        Path configFile = tempDir.resolve("strict-central.txt");
        Files.writeString(configFile, "org.graylog\n");

        // Load with absolute path
        StrictFilterConfiguration config = StrictFilterConfiguration.load(
                tempDir.toAbsolutePath().toString(),
                tempDir
        );

        assertFalse(config.isEmpty(), "Configuration should load from absolute path");
    }

    @Test
    void testIgnoreNonConfigFiles(@TempDir Path tempDir) throws Exception {
        // Create various files, only strict-*.txt should be loaded
        Files.writeString(tempDir.resolve("strict-central.txt"), "org.graylog\n");
        Files.writeString(tempDir.resolve("README.md"), "This is a readme\n");
        Files.writeString(tempDir.resolve("config.txt"), "not a strict config\n");
        Files.writeString(tempDir.resolve("strict-backup.bak"), "backup file\n");

        StrictFilterConfiguration config = StrictFilterConfiguration.load(
                tempDir.toString(),
                tempDir
        );

        assertFalse(config.isEmpty(), "Configuration should load only strict-*.txt files");
    }

    @Test
    void testEmptyConfigFile(@TempDir Path tempDir) throws Exception {
        // Create an empty config file
        Path configFile = tempDir.resolve("strict-central.txt");
        Files.writeString(configFile, "");

        StrictFilterConfiguration config = StrictFilterConfiguration.load(
                tempDir.toString(),
                tempDir
        );

        // Empty file means no groupIds configured for this repository
        assertTrue(config.isEmpty(), "Configuration should be empty when config file is empty");
    }

    @Test
    void testConfigFileWithOnlyComments(@TempDir Path tempDir) throws Exception {
        Path configFile = tempDir.resolve("strict-central.txt");
        Files.writeString(configFile,
                "# This is a comment\n" +
                        "# Another comment\n" +
                        "\n" +
                        "   \n"
        );

        StrictFilterConfiguration config = StrictFilterConfiguration.load(
                tempDir.toString(),
                tempDir
        );

        assertTrue(config.isEmpty(), "Configuration with only comments should be empty");
    }
}
