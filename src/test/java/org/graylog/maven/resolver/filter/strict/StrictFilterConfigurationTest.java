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
        // Create config file with repository rules
        Path configFile = tempDir.resolve("strict.properties");
        Files.writeString(configFile,
                "# Comment line\n" +
                        "repo.central.allow = org.graylog,org.apache.commons,org.springframework\n" +
                        "\n" +
                        "# Another comment\n"
        );

        StrictFilterConfiguration config = StrictFilterConfiguration.load(
                tempDir.toString(),
                tempDir
        );

        assertFalse(config.isEmpty(), "Configuration should not be empty");
    }

    @Test
    void testMultipleRepositories(@TempDir Path tempDir) throws Exception {
        // Create config file with rules for multiple repositories
        Path configFile = tempDir.resolve("strict.properties");
        Files.writeString(configFile,
                "repo.central.allow = org.graylog\n" +
                        "repo.company-repo.allow = com.company\n" +
                        "repo.shibboleth.allow = org.opensaml,net.shibboleth\n"
        );

        StrictFilterConfiguration config = StrictFilterConfiguration.load(
                tempDir.toString(),
                tempDir
        );

        assertFalse(config.isEmpty(), "Configuration should not be empty");
    }

    @Test
    void testArtifactAllowed(@TempDir Path tempDir) throws Exception {
        // Create config file
        Path configFile = tempDir.resolve("strict.properties");
        Files.writeString(configFile, "repo.central.allow = org.graylog,org.apache.commons\n");

        StrictFilterConfiguration config = StrictFilterConfiguration.load(
                tempDir.toString(),
                tempDir
        );

        // Test exact match
        Artifact artifact1 = new DefaultArtifact("org.graylog:my-artifact:1.0");
        assertTrue(config.isArtifactAllowed("central", artifact1),
                "Artifact with matching groupId should be allowed");

        // Test prefix match (legacy behavior without wildcard)
        Artifact artifact2 = new DefaultArtifact("org.graylog.plugin:another-artifact:1.0");
        assertTrue(config.isArtifactAllowed("central", artifact2),
                "Artifact with matching groupId prefix should be allowed");

        // Test not allowed (default deny)
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
        Path configFile = tempDir.resolve("strict.properties");
        Files.writeString(configFile, "repo.central.allow = org.graylog\n");

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
        Files.writeString(subdir.resolve("strict.properties"), "repo.central.allow = org.graylog\n");

        // Load with relative path
        StrictFilterConfiguration config = StrictFilterConfiguration.load(
                ".remoteRepositoryFilters/strict",
                tempDir
        );

        assertFalse(config.isEmpty(), "Configuration should load from relative path");
    }

    @Test
    void testAbsoluteBasedir(@TempDir Path tempDir) throws Exception {
        Path configFile = tempDir.resolve("strict.properties");
        Files.writeString(configFile, "repo.central.allow = org.graylog\n");

        // Load with absolute path
        StrictFilterConfiguration config = StrictFilterConfiguration.load(
                tempDir.toAbsolutePath().toString(),
                tempDir
        );

        assertFalse(config.isEmpty(), "Configuration should load from absolute path");
    }

    @Test
    void testIgnoreNonConfigFiles(@TempDir Path tempDir) throws Exception {
        // Create various files, only strict.properties should be loaded
        Files.writeString(tempDir.resolve("strict.properties"), "repo.central.allow = org.graylog\n");
        Files.writeString(tempDir.resolve("README.md"), "This is a readme\n");
        Files.writeString(tempDir.resolve("config.txt"), "not a strict config\n");
        Files.writeString(tempDir.resolve("strict-backup.bak"), "backup file\n");

        StrictFilterConfiguration config = StrictFilterConfiguration.load(
                tempDir.toString(),
                tempDir
        );

        assertFalse(config.isEmpty(), "Configuration should load only strict.properties file");
    }

    @Test
    void testEmptyConfigFile(@TempDir Path tempDir) throws Exception {
        // Create an empty config file
        Path configFile = tempDir.resolve("strict.properties");
        Files.writeString(configFile, "");

        StrictFilterConfiguration config = StrictFilterConfiguration.load(
                tempDir.toString(),
                tempDir
        );

        // Empty file means no groupIds configured for any repository
        assertTrue(config.isEmpty(), "Configuration should be empty when config file is empty");
    }

    @Test
    void testConfigFileWithOnlyComments(@TempDir Path tempDir) throws Exception {
        Path configFile = tempDir.resolve("strict.properties");
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

    @Test
    void testAllowDenyFormat(@TempDir Path tempDir) throws Exception {
        Path configFile = tempDir.resolve("strict.properties");
        Files.writeString(configFile,
                "# Shibboleth repository\n" +
                        "repo.shibboleth.allow = org.opensaml,net.shibboleth\n" +
                        "repo.shibboleth.deny = org.opensaml.internal*\n" +
                        "\n" +
                        "# Maven Central - multiple groupIds\n" +
                        "repo.central.allow = org.graylog,org.apache.maven,org.springframework\n" +
                        "\n" +
                        "# Company repo - single groupId\n" +
                        "repo.company.allow = com.company\n"
        );

        StrictFilterConfiguration config = StrictFilterConfiguration.load(
                tempDir.toString(),
                tempDir
        );

        assertFalse(config.isEmpty(), "Configuration should not be empty");

        // Test Shibboleth repository - allowed
        Artifact shibArtifact = new DefaultArtifact("org.opensaml:opensaml-core:4.0.0");
        assertTrue(config.isArtifactAllowed("shibboleth", shibArtifact),
                "Artifact from allowed groupId should be accepted");

        // Test Shibboleth repository - denied by glob pattern (org.opensaml.internal*)
        Artifact shibInternalArtifact = new DefaultArtifact("org.opensaml.internal:something:1.0");
        assertFalse(config.isArtifactAllowed("shibboleth", shibInternalArtifact),
                "Artifact matching deny pattern should be rejected");

        // Test second allowed groupId
        Artifact netShibArtifact = new DefaultArtifact("net.shibboleth:utilities:8.0.0");
        assertTrue(config.isArtifactAllowed("shibboleth", netShibArtifact),
                "Artifact from second allowed groupId should be accepted");

        // Test that other groupIds are rejected (default deny)
        Artifact otherArtifact = new DefaultArtifact("com.example:something:1.0");
        assertFalse(config.isArtifactAllowed("shibboleth", otherArtifact),
                "Artifact from non-allowed groupId should be rejected");
    }

    @Test
    void testGlobPatterns(@TempDir Path tempDir) throws Exception {
        Path configFile = tempDir.resolve("strict.properties");
        Files.writeString(configFile,
                "repo.test.allow = com.google,com.foo*\n" +
                        "repo.test.deny = com.google.internal.*\n"
        );

        StrictFilterConfiguration config = StrictFilterConfiguration.load(
                tempDir.toString(),
                tempDir
        );

        // Test exact match
        Artifact exact = new DefaultArtifact("com.google:guava:30.0");
        assertTrue(config.isArtifactAllowed("test", exact),
                "Exact match should be allowed");

        // Test prefix match (without wildcard in pattern)
        Artifact prefix = new DefaultArtifact("com.google.common:base:1.0");
        assertTrue(config.isArtifactAllowed("test", prefix),
                "Prefix match should be allowed");

        // Test glob with * - denied
        Artifact denied = new DefaultArtifact("com.google.internal.tools:something:1.0");
        assertFalse(config.isArtifactAllowed("test", denied),
                "Should match deny pattern com.google.internal.*");

        // Test wildcard in allow pattern
        Artifact wildcardMatch1 = new DefaultArtifact("com.foo:bar:1.0");
        assertTrue(config.isArtifactAllowed("test", wildcardMatch1),
                "Should match com.foo* pattern");

        Artifact wildcardMatch2 = new DefaultArtifact("com.foobar:baz:1.0");
        assertTrue(config.isArtifactAllowed("test", wildcardMatch2),
                "Should match com.foo* pattern");

        // Test non-matching
        Artifact noMatch = new DefaultArtifact("com.example:test:1.0");
        assertFalse(config.isArtifactAllowed("test", noMatch),
                "Should not match any pattern");
    }

    @Test
    void testDefaultDenyAll(@TempDir Path tempDir) throws Exception {
        Path configFile = tempDir.resolve("strict.properties");
        Files.writeString(configFile,
                "# Only allow specific artifacts\n" +
                        "repo.strict.allow = org.graylog\n"
        );

        StrictFilterConfiguration config = StrictFilterConfiguration.load(
                tempDir.toString(),
                tempDir
        );

        // Allowed
        Artifact allowed = new DefaultArtifact("org.graylog:server:1.0");
        assertTrue(config.isArtifactAllowed("strict", allowed),
                "Explicitly allowed groupId should work");

        // Everything else denied by default
        Artifact denied1 = new DefaultArtifact("com.google:guava:30.0");
        assertFalse(config.isArtifactAllowed("strict", denied1),
                "Should be denied by default");

        Artifact denied2 = new DefaultArtifact("org.apache:commons:1.0");
        assertFalse(config.isArtifactAllowed("strict", denied2),
                "Should be denied by default");
    }

    @Test
    void testInvalidPropertyLines(@TempDir Path tempDir) throws Exception {
        Path configFile = tempDir.resolve("strict.properties");
        Files.writeString(configFile,
                "# Valid line\n" +
                        "repo.central.allow = org.graylog\n" +
                        "\n" +
                        "# Invalid lines that should be skipped\n" +
                        "repo.invalid = org.apache\n" +  // Missing .allow/.deny
                        "notrepo.something.allow = org.apache\n" +  // Missing repo. prefix
                        "repo..allow = org.empty\n" +  // Empty repo ID
                        "\n" +
                        "# Another valid line\n" +
                        "repo.company.allow = com.company\n"
        );

        StrictFilterConfiguration config = StrictFilterConfiguration.load(
                tempDir.toString(),
                tempDir
        );

        assertFalse(config.isEmpty(), "Configuration should load valid lines");

        // Should only have 'central' and 'company'
        Artifact graylogArtifact = new DefaultArtifact("org.graylog:something:1.0");
        assertTrue(config.isArtifactAllowed("central", graylogArtifact),
                "Valid repo should work");

        Artifact companyArtifact = new DefaultArtifact("com.company:app:1.0");
        assertTrue(config.isArtifactAllowed("company", companyArtifact),
                "Another valid repo should work");
    }

    @Test
    void testWhitespaceHandling(@TempDir Path tempDir) throws Exception {
        Path configFile = tempDir.resolve("strict.properties");
        Files.writeString(configFile,
                "  repo.central.allow   =   org.graylog  ,  org.apache.commons  \n" +
                        "repo.company.allow=com.company,   com.other   \n"
        );

        StrictFilterConfiguration config = StrictFilterConfiguration.load(
                tempDir.toString(),
                tempDir
        );

        assertFalse(config.isEmpty(), "Configuration should handle whitespace");

        Artifact artifact1 = new DefaultArtifact("org.graylog:test:1.0");
        assertTrue(config.isArtifactAllowed("central", artifact1),
                "Should trim whitespace from groupIds");

        Artifact artifact2 = new DefaultArtifact("org.apache.commons:lang:3.0");
        assertTrue(config.isArtifactAllowed("central", artifact2),
                "Should trim whitespace from groupIds");
    }

    @Test
    void testRepositoryNamesWithDots(@TempDir Path tempDir) throws Exception {
        Path configFile = tempDir.resolve("strict.properties");
        Files.writeString(configFile,
                "# Repository names can contain dots\n" +
                        "repo.apache.snapshots.allow = org.apache\n" +
                        "repo.spring.milestone.allow = org.springframework\n" +
                        "repo.company.internal.releases.allow = com.company\n"
        );

        StrictFilterConfiguration config = StrictFilterConfiguration.load(
                tempDir.toString(),
                tempDir
        );

        assertFalse(config.isEmpty(), "Configuration should not be empty");

        // Test apache.snapshots repository
        Artifact apacheArtifact = new DefaultArtifact("org.apache:commons:1.0");
        assertTrue(config.isArtifactAllowed("apache.snapshots", apacheArtifact),
                "Should work with repository name containing dots");

        // Test spring.milestone repository
        Artifact springArtifact = new DefaultArtifact("org.springframework:spring-core:5.0");
        assertTrue(config.isArtifactAllowed("spring.milestone", springArtifact),
                "Should work with repository name containing dots");

        // Test company.internal.releases repository (multiple dots)
        Artifact companyArtifact = new DefaultArtifact("com.company:app:1.0");
        assertTrue(config.isArtifactAllowed("company.internal.releases", companyArtifact),
                "Should work with repository name containing multiple dots");

        // Test that deny still works
        Files.writeString(configFile,
                "repo.apache.snapshots.allow = org.apache\n" +
                        "repo.apache.snapshots.deny = org.apache.internal*\n"
        );

        config = StrictFilterConfiguration.load(tempDir.toString(), tempDir);

        Artifact allowedArtifact = new DefaultArtifact("org.apache:commons:1.0");
        assertTrue(config.isArtifactAllowed("apache.snapshots", allowedArtifact),
                "Should allow non-internal apache artifacts");

        Artifact deniedArtifact = new DefaultArtifact("org.apache.internal:test:1.0");
        assertFalse(config.isArtifactAllowed("apache.snapshots", deniedArtifact),
                "Should deny internal apache artifacts");
    }
}
