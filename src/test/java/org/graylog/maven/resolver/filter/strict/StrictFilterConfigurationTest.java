package org.graylog.maven.resolver.filter.strict;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.metadata.DefaultMetadata;
import org.eclipse.aether.metadata.Metadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link StrictFilterConfiguration}.
 */
class StrictFilterConfigurationTest {

    @Test
    void testEmptyConfiguration(@TempDir Path tempDir) {
        final StrictFilterConfiguration config = StrictFilterConfiguration.load(
                tempDir.toString(),
                tempDir
        );

        assertTrue(config.isEmpty(), "Configuration should be empty when no config files exist");
        assertEquals(tempDir, config.getBasedir(), "Basedir should match");
    }

    @Test
    void testNonExistentDirectory(@TempDir Path tempDir) {
        final Path nonExistent = tempDir.resolve("does-not-exist");

        final StrictFilterConfiguration config = StrictFilterConfiguration.load(
                nonExistent.toString(),
                tempDir
        );

        assertTrue(config.isEmpty(), "Configuration should be empty when directory doesn't exist");
    }

    @Test
    void testLoadConfiguration(@TempDir Path tempDir) throws Exception {
        StrictPropertiesTestHelper.writeStrictProperties(tempDir, """
                # Comment line
                repo.central.allow = org.graylog,org.apache.commons,org.springframework
                
                # Another comment
                """);

        final StrictFilterConfiguration config = StrictFilterConfiguration.load(
                tempDir.toString(),
                tempDir
        );

        assertFalse(config.isEmpty(), "Configuration should not be empty");
    }

    @Test
    void testMultipleRepositories(@TempDir Path tempDir) throws Exception {
        StrictPropertiesTestHelper.writeStrictProperties(tempDir, """
                repo.central.allow = org.graylog
                repo.company-repo.allow = com.company
                repo.shibboleth.allow = org.opensaml,net.shibboleth
                """);

        final StrictFilterConfiguration config = StrictFilterConfiguration.load(
                tempDir.toString(),
                tempDir
        );

        assertFalse(config.isEmpty(), "Configuration should not be empty");
    }

    @Test
    void testArtifactAllowed(@TempDir Path tempDir) throws Exception {
        StrictPropertiesTestHelper.writeStrictProperties(tempDir, "repo.central.allow = org.graylog*,org.apache.commons*\n");

        final StrictFilterConfiguration config = StrictFilterConfiguration.load(
                tempDir.toString(),
                tempDir
        );

        // Test exact match
        final Artifact artifact1 = new DefaultArtifact("org.graylog:my-artifact:1.0");
        assertTrue(config.isArtifactAllowed("central", artifact1),
                "Artifact with matching groupId should be allowed");

        // Test wildcard match - sub-packages
        final Artifact artifact2 = new DefaultArtifact("org.graylog.plugin:another-artifact:1.0");
        assertTrue(config.isArtifactAllowed("central", artifact2),
                "Artifact with matching groupId wildcard should be allowed");

        // Test not allowed (default deny)
        final Artifact artifact3 = new DefaultArtifact("com.example:different-artifact:1.0");
        assertFalse(config.isArtifactAllowed("central", artifact3),
                "Artifact with non-matching groupId should not be allowed");
    }

    @Test
    void testArtifactBlockedNoConfig(@TempDir Path tempDir) {
        final StrictFilterConfiguration config = StrictFilterConfiguration.load(
                tempDir.toString(),
                tempDir
        );

        // When no config exists for a repository, all artifacts should be BLOCKED (fail-secure)
        final Artifact artifact = new DefaultArtifact("any.group:artifact:1.0");
        assertFalse(config.isArtifactAllowed("central", artifact),
                "Artifact should be blocked when no config exists for repository (fail-secure)");
    }

    @Test
    void testMetadataAllowed(@TempDir Path tempDir) throws Exception {
        StrictPropertiesTestHelper.writeStrictProperties(tempDir, "repo.central.allow = org.graylog\n");

        final StrictFilterConfiguration config = StrictFilterConfiguration.load(
                tempDir.toString(),
                tempDir
        );

        // Test with groupId
        final Metadata metadata1 = new DefaultMetadata("org.graylog", "maven-metadata.xml",
                Metadata.Nature.RELEASE_OR_SNAPSHOT);
        assertTrue(config.isMetadataAllowed("central", metadata1),
                "Metadata with matching groupId should be allowed");

        // Test without groupId (repository-level metadata)
        final Metadata metadata2 = new DefaultMetadata("maven-metadata.xml", Metadata.Nature.RELEASE_OR_SNAPSHOT);
        assertTrue(config.isMetadataAllowed("central", metadata2),
                "Metadata without groupId should be allowed");

        // Test with non-matching groupId
        final Metadata metadata3 = new DefaultMetadata("com.example", "maven-metadata.xml",
                Metadata.Nature.RELEASE_OR_SNAPSHOT);
        assertFalse(config.isMetadataAllowed("central", metadata3),
                "Metadata with non-matching groupId should not be allowed");
    }

    @Test
    void testRelativeBasedir(@TempDir Path tempDir) throws Exception {
        // Create a subdirectory
        final Path subdir = tempDir.resolve(".remoteRepositoryFilters/strict");
        Files.createDirectories(subdir);
        Files.writeString(subdir.resolve("strict.properties"), "repo.central.allow = org.graylog\n");

        // Load with relative path
        final StrictFilterConfiguration config = StrictFilterConfiguration.load(
                ".remoteRepositoryFilters/strict",
                tempDir
        );

        assertFalse(config.isEmpty(), "Configuration should load from relative path");
    }

    @Test
    void testAbsoluteBasedir(@TempDir Path tempDir) throws Exception {
        StrictPropertiesTestHelper.writeStrictProperties(tempDir, "repo.central.allow = org.graylog\n");

        // Load with absolute path
        final StrictFilterConfiguration config = StrictFilterConfiguration.load(
                tempDir.toAbsolutePath().toString(),
                tempDir
        );

        assertFalse(config.isEmpty(), "Configuration should load from absolute path");
    }

    @Test
    void testIgnoreNonConfigFiles(@TempDir Path tempDir) throws Exception {
        // Create various files, only strict.properties should be loaded
        StrictPropertiesTestHelper.writeStrictProperties(tempDir, "repo.central.allow = org.graylog\n");
        Files.writeString(tempDir.resolve("README.md"), "This is a readme\n");
        Files.writeString(tempDir.resolve("config.txt"), "not a strict config\n");
        Files.writeString(tempDir.resolve("strict-backup.bak"), "backup file\n");

        final StrictFilterConfiguration config = StrictFilterConfiguration.load(
                tempDir.toString(),
                tempDir
        );

        assertFalse(config.isEmpty(), "Configuration should load only strict.properties file");
    }

    @Test
    void testEmptyConfigFile(@TempDir Path tempDir) throws Exception {
        StrictPropertiesTestHelper.writeStrictProperties(tempDir, "");

        final StrictFilterConfiguration config = StrictFilterConfiguration.load(
                tempDir.toString(),
                tempDir
        );

        // Empty file means no groupIds configured for any repository
        assertTrue(config.isEmpty(), "Configuration should be empty when config file is empty");
    }

    @Test
    void testConfigFileWithOnlyComments(@TempDir Path tempDir) throws Exception {
        StrictPropertiesTestHelper.writeStrictProperties(tempDir, """
                # This is a comment
                # Another comment
                
                
                """);

        final StrictFilterConfiguration config = StrictFilterConfiguration.load(
                tempDir.toString(),
                tempDir
        );

        assertTrue(config.isEmpty(), "Configuration with only comments should be empty");
    }

    @Test
    void testAllowDenyFormat(@TempDir Path tempDir) throws Exception {
        StrictPropertiesTestHelper.writeStrictProperties(tempDir, """
                # Shibboleth repository
                repo.shibboleth.allow = org.opensaml,net.shibboleth
                repo.shibboleth.deny = org.opensaml.internal*
                
                # Maven Central - multiple groupIds
                repo.central.allow = org.graylog,org.apache.maven,org.springframework
                
                # Company repo - single groupId
                repo.company.allow = com.company
                """);

        final StrictFilterConfiguration config = StrictFilterConfiguration.load(
                tempDir.toString(),
                tempDir
        );

        assertFalse(config.isEmpty(), "Configuration should not be empty");

        // Test Shibboleth repository - allowed
        final Artifact shibArtifact = new DefaultArtifact("org.opensaml:opensaml-core:4.0.0");
        assertTrue(config.isArtifactAllowed("shibboleth", shibArtifact),
                "Artifact from allowed groupId should be accepted");

        // Test Shibboleth repository - denied by glob pattern (org.opensaml.internal*)
        final Artifact shibInternalArtifact = new DefaultArtifact("org.opensaml.internal:something:1.0");
        assertFalse(config.isArtifactAllowed("shibboleth", shibInternalArtifact),
                "Artifact matching deny pattern should be rejected");

        // Test second allowed groupId
        final Artifact netShibArtifact = new DefaultArtifact("net.shibboleth:utilities:8.0.0");
        assertTrue(config.isArtifactAllowed("shibboleth", netShibArtifact),
                "Artifact from second allowed groupId should be accepted");

        // Test that other groupIds are rejected (default deny)
        final Artifact otherArtifact = new DefaultArtifact("com.example:something:1.0");
        assertFalse(config.isArtifactAllowed("shibboleth", otherArtifact),
                "Artifact from non-allowed groupId should be rejected");
    }

    @Test
    void testGlobPatterns(@TempDir Path tempDir) throws Exception {
        StrictPropertiesTestHelper.writeStrictProperties(tempDir, """
                repo.test.allow = com.google*,com.foo*
                repo.test.deny = com.google.internal.*
                """);

        final StrictFilterConfiguration config = StrictFilterConfiguration.load(
                tempDir.toString(),
                tempDir
        );

        // Test exact match
        final Artifact exact = new DefaultArtifact("com.google:guava:30.0");
        assertTrue(config.isArtifactAllowed("test", exact),
                "Exact match should be allowed");

        // Test wildcard match - sub-packages
        final Artifact subPackage = new DefaultArtifact("com.google.common:base:1.0");
        assertTrue(config.isArtifactAllowed("test", subPackage),
                "Sub-package should match wildcard pattern");

        // Test glob with * - denied
        final Artifact denied = new DefaultArtifact("com.google.internal.tools:something:1.0");
        assertFalse(config.isArtifactAllowed("test", denied),
                "Should match deny pattern com.google.internal.*");

        // Test wildcard in allow pattern
        final Artifact wildcardMatch1 = new DefaultArtifact("com.foo:bar:1.0");
        assertTrue(config.isArtifactAllowed("test", wildcardMatch1),
                "Should match com.foo* pattern");

        final Artifact wildcardMatch2 = new DefaultArtifact("com.foobar:baz:1.0");
        assertTrue(config.isArtifactAllowed("test", wildcardMatch2),
                "Should match com.foo* pattern");

        // Test non-matching
        final Artifact noMatch = new DefaultArtifact("com.example:test:1.0");
        assertFalse(config.isArtifactAllowed("test", noMatch),
                "Should not match any pattern");
    }

    @Test
    void testDefaultDenyAll(@TempDir Path tempDir) throws Exception {
        StrictPropertiesTestHelper.writeStrictProperties(tempDir, """
                # Only allow specific artifacts
                repo.strict.allow = org.graylog
                """);

        final StrictFilterConfiguration config = StrictFilterConfiguration.load(
                tempDir.toString(),
                tempDir
        );

        // Allowed
        final Artifact allowed = new DefaultArtifact("org.graylog:server:1.0");
        assertTrue(config.isArtifactAllowed("strict", allowed),
                "Explicitly allowed groupId should work");

        // Everything else denied by default
        final Artifact denied1 = new DefaultArtifact("com.google:guava:30.0");
        assertFalse(config.isArtifactAllowed("strict", denied1),
                "Should be denied by default");

        final Artifact denied2 = new DefaultArtifact("org.apache:commons:1.0");
        assertFalse(config.isArtifactAllowed("strict", denied2),
                "Should be denied by default");
    }

    @Test
    void testInvalidPropertyLines(@TempDir Path tempDir) throws Exception {
        StrictPropertiesTestHelper.writeStrictProperties(tempDir, """
                # Valid line
                repo.central.allow = org.graylog
                
                # Invalid lines that should be skipped
                repo.invalid = org.apache
                notrepo.something.allow = org.apache
                repo..allow = org.empty
                
                # Another valid line
                repo.company.allow = com.company
                """);

        final StrictFilterConfiguration config = StrictFilterConfiguration.load(
                tempDir.toString(),
                tempDir
        );

        assertFalse(config.isEmpty(), "Configuration should load valid lines");

        // Should only have 'central' and 'company'
        final Artifact graylogArtifact = new DefaultArtifact("org.graylog:something:1.0");
        assertTrue(config.isArtifactAllowed("central", graylogArtifact),
                "Valid repo should work");

        final Artifact companyArtifact = new DefaultArtifact("com.company:app:1.0");
        assertTrue(config.isArtifactAllowed("company", companyArtifact),
                "Another valid repo should work");
    }

    @Test
    void testWhitespaceHandling(@TempDir Path tempDir) throws Exception {
        StrictPropertiesTestHelper.writeStrictProperties(tempDir, """
                  repo.central.allow   =   org.graylog  ,  org.apache.commons
                repo.company.allow=com.company,   com.other
                """);

        final StrictFilterConfiguration config = StrictFilterConfiguration.load(
                tempDir.toString(),
                tempDir
        );

        assertFalse(config.isEmpty(), "Configuration should handle whitespace");

        final Artifact artifact1 = new DefaultArtifact("org.graylog:test:1.0");
        assertTrue(config.isArtifactAllowed("central", artifact1),
                "Should trim whitespace from groupIds");

        final Artifact artifact2 = new DefaultArtifact("org.apache.commons:lang:3.0");
        assertTrue(config.isArtifactAllowed("central", artifact2),
                "Should trim whitespace from groupIds");
    }

    @Test
    void testArtifactCoordinatePatterns(@TempDir Path tempDir) throws Exception {
        StrictPropertiesTestHelper.writeStrictProperties(tempDir, """
                # Allow all artifacts from com.opensaml
                repo.test.allow = com.opensaml:*
                # Allow only test-* artifacts from com.foobar
                repo.test2.allow = com.foobar:test-*
                """);

        final StrictFilterConfiguration config = StrictFilterConfiguration.load(
                tempDir.toString(),
                tempDir
        );

        // Test com.opensaml:* - should allow any artifact
        final Artifact artifact1 = new DefaultArtifact("com.opensaml:opensaml-core:4.0.0");
        assertTrue(config.isArtifactAllowed("test", artifact1),
                "Should allow com.opensaml:opensaml-core");

        final Artifact artifact2 = new DefaultArtifact("com.opensaml:anything:1.0");
        assertTrue(config.isArtifactAllowed("test", artifact2),
                "Should allow com.opensaml:anything");

        // Different groupId should be denied
        final Artifact artifact3 = new DefaultArtifact("com.other:something:1.0");
        assertFalse(config.isArtifactAllowed("test", artifact3),
                "Should deny com.other:something");

        // Test com.foobar:test-* - should only allow test-* artifacts
        final Artifact artifact4 = new DefaultArtifact("com.foobar:test-utils:1.0");
        assertTrue(config.isArtifactAllowed("test2", artifact4),
                "Should allow com.foobar:test-utils");

        final Artifact artifact5 = new DefaultArtifact("com.foobar:test-core:1.0");
        assertTrue(config.isArtifactAllowed("test2", artifact5),
                "Should allow com.foobar:test-core");

        // Non-test artifact should be denied
        final Artifact artifact6 = new DefaultArtifact("com.foobar:production-lib:1.0");
        assertFalse(config.isArtifactAllowed("test2", artifact6),
                "Should deny com.foobar:production-lib");
    }

    @Test
    void testMixedGroupIdAndCoordinatePatterns(@TempDir Path tempDir) throws Exception {
        StrictPropertiesTestHelper.writeStrictProperties(tempDir, """
                # Mix groupId-only and coordinate patterns
                repo.mixed.allow = org.graylog,com.opensaml:*,com.test:lib-*
                """);

        final StrictFilterConfiguration config = StrictFilterConfiguration.load(
                tempDir.toString(),
                tempDir
        );

        // Test groupId-only pattern (org.graylog) - exact match
        final Artifact artifact1 = new DefaultArtifact("org.graylog:server:1.0");
        assertTrue(config.isArtifactAllowed("mixed", artifact1),
                "Should allow org.graylog:server via groupId pattern");

        // Sub-package should NOT match without wildcard
        final Artifact artifact2 = new DefaultArtifact("org.graylog.plugin:plugin-api:1.0");
        assertFalse(config.isArtifactAllowed("mixed", artifact2),
                "Should NOT allow org.graylog.plugin without wildcard in pattern");

        // Test coordinate pattern with wildcard (com.opensaml:*)
        final Artifact artifact3 = new DefaultArtifact("com.opensaml:opensaml-core:4.0.0");
        assertTrue(config.isArtifactAllowed("mixed", artifact3),
                "Should allow com.opensaml:opensaml-core via coordinate pattern");

        // Test coordinate pattern with prefix (com.test:lib-*)
        final Artifact artifact4 = new DefaultArtifact("com.test:lib-utils:1.0");
        assertTrue(config.isArtifactAllowed("mixed", artifact4),
                "Should allow com.test:lib-utils via coordinate pattern");

        final Artifact artifact5 = new DefaultArtifact("com.test:other-utils:1.0");
        assertFalse(config.isArtifactAllowed("mixed", artifact5),
                "Should deny com.test:other-utils (doesn't match lib-* pattern)");

        // Test non-matching groupId
        final Artifact artifact6 = new DefaultArtifact("com.other:something:1.0");
        assertFalse(config.isArtifactAllowed("mixed", artifact6),
                "Should deny com.other:something");
    }

    @Test
    void testCoordinateDenyPatterns(@TempDir Path tempDir) throws Exception {
        StrictPropertiesTestHelper.writeStrictProperties(tempDir, """
                # Allow all from com.opensaml, but deny internal artifacts
                repo.test.allow = com.opensaml:*
                repo.test.deny = com.opensaml:*-internal
                """);

        final StrictFilterConfiguration config = StrictFilterConfiguration.load(
                tempDir.toString(),
                tempDir
        );

        // Allowed artifact
        final Artifact artifact1 = new DefaultArtifact("com.opensaml:opensaml-core:4.0.0");
        assertTrue(config.isArtifactAllowed("test", artifact1),
                "Should allow com.opensaml:opensaml-core");

        // Denied by pattern
        final Artifact artifact2 = new DefaultArtifact("com.opensaml:utils-internal:1.0");
        assertFalse(config.isArtifactAllowed("test", artifact2),
                "Should deny com.opensaml:utils-internal");

        final Artifact artifact3 = new DefaultArtifact("com.opensaml:test-internal:1.0");
        assertFalse(config.isArtifactAllowed("test", artifact3),
                "Should deny com.opensaml:test-internal");
    }

    @Test
    void testExactArtifactIdMatch(@TempDir Path tempDir) throws Exception {
        StrictPropertiesTestHelper.writeStrictProperties(tempDir, """
                # Allow only specific artifacts
                repo.test.allow = com.google:guava,com.google:gson
                """);

        final StrictFilterConfiguration config = StrictFilterConfiguration.load(
                tempDir.toString(),
                tempDir
        );

        // Exact match - guava
        final Artifact artifact1 = new DefaultArtifact("com.google:guava:30.0");
        assertTrue(config.isArtifactAllowed("test", artifact1),
                "Should allow com.google:guava");

        // Exact match - gson
        final Artifact artifact2 = new DefaultArtifact("com.google:gson:2.8.0");
        assertTrue(config.isArtifactAllowed("test", artifact2),
                "Should allow com.google:gson");

        // Non-matching artifact from same groupId
        final Artifact artifact3 = new DefaultArtifact("com.google:truth:1.0");
        assertFalse(config.isArtifactAllowed("test", artifact3),
                "Should deny com.google:truth");
    }

    @Test
    void testExactGroupIdMatching(@TempDir Path tempDir) throws Exception {
        StrictPropertiesTestHelper.writeStrictProperties(tempDir, """
                # Exact groupId matching (no prefix match)
                repo.test.allow = org.graylog,org.apache.commons
                """);

        final StrictFilterConfiguration config = StrictFilterConfiguration.load(
                tempDir.toString(),
                tempDir
        );

        // Exact match should work
        final Artifact artifact1 = new DefaultArtifact("org.graylog:server:1.0");
        assertTrue(config.isArtifactAllowed("test", artifact1),
                "Exact groupId match should be allowed");

        // Sub-package should NOT match without wildcard
        final Artifact artifact2 = new DefaultArtifact("org.graylog.plugin:api:2.0");
        assertFalse(config.isArtifactAllowed("test", artifact2),
                "Sub-package should NOT match without wildcard");

        // Exact match should work
        final Artifact artifact3 = new DefaultArtifact("org.apache.commons:commons-lang3:3.0");
        assertTrue(config.isArtifactAllowed("test", artifact3),
                "Exact groupId match should be allowed");

        // Sub-package should NOT match
        final Artifact artifact4 = new DefaultArtifact("org.apache.commons.io:commons-io:2.0");
        assertFalse(config.isArtifactAllowed("test", artifact4),
                "Sub-package should NOT match without wildcard");
    }

    @Test
    void testRepositoryNamesWithDots(@TempDir Path tempDir) throws Exception {
        StrictPropertiesTestHelper.writeStrictProperties(tempDir, """
                # Repository names can contain dots
                repo.apache.snapshots.allow = org.apache
                repo.spring.milestone.allow = org.springframework
                repo.company.internal.releases.allow = com.company
                """);

        StrictFilterConfiguration config = StrictFilterConfiguration.load(
                tempDir.toString(),
                tempDir
        );

        assertFalse(config.isEmpty(), "Configuration should not be empty");

        // Test apache.snapshots repository
        final Artifact apacheArtifact = new DefaultArtifact("org.apache:commons:1.0");
        assertTrue(config.isArtifactAllowed("apache.snapshots", apacheArtifact),
                "Should work with repository name containing dots");

        // Test spring.milestone repository
        final Artifact springArtifact = new DefaultArtifact("org.springframework:spring-core:5.0");
        assertTrue(config.isArtifactAllowed("spring.milestone", springArtifact),
                "Should work with repository name containing dots");

        // Test company.internal.releases repository (multiple dots)
        final Artifact companyArtifact = new DefaultArtifact("com.company:app:1.0");
        assertTrue(config.isArtifactAllowed("company.internal.releases", companyArtifact),
                "Should work with repository name containing multiple dots");

        // Test that deny still works
        StrictPropertiesTestHelper.writeStrictProperties(tempDir, """
                repo.apache.snapshots.allow = org.apache
                repo.apache.snapshots.deny = org.apache.internal*
                """);

        config = StrictFilterConfiguration.load(tempDir.toString(), tempDir);

        final Artifact allowedArtifact = new DefaultArtifact("org.apache:commons:1.0");
        assertTrue(config.isArtifactAllowed("apache.snapshots", allowedArtifact),
                "Should allow non-internal apache artifacts");

        final Artifact deniedArtifact = new DefaultArtifact("org.apache.internal:test:1.0");
        assertFalse(config.isArtifactAllowed("apache.snapshots", deniedArtifact),
                "Should deny internal apache artifacts");
    }

    @Test
    void testFileSizeValidationExceedsLimit(@TempDir Path tempDir) throws Exception {
        // Create a file larger than 10MB (MAX_FILE_SIZE)
        final Path configFile = tempDir.resolve("strict.properties");
        final int fileSize = 11 * 1024 * 1024; // 11MB
        final byte[] largeContent = new byte[fileSize];
        Files.write(configFile, largeContent);

        final StrictFilterConfiguration config = StrictFilterConfiguration.load(
                tempDir.toString(),
                tempDir
        );

        assertTrue(config.isEmpty(),
                "Configuration should be empty when file exceeds size limit");
    }

    @Test
    void testFileSizeValidationAtLimit(@TempDir Path tempDir) throws Exception {
        // Create a file at exactly 10MB (MAX_FILE_SIZE)
        final Path configFile = tempDir.resolve("strict.properties");

        // Create a valid properties content that's close to 10MB
        final int maxFileSize = 10 * 1024 * 1024; // 10MB = 10,485,760 bytes
        final int targetSize = maxFileSize - 1024; // Just under 10MB
        final StringBuilder content = new StringBuilder(targetSize);
        content.append("repo.central.allow = org.graylog\n");

        // Pad with comments to reach near 10MB, ensuring we don't exceed targetSize
        final String paddingLine = "# This is a comment line to pad the file size\n";
        while (content.length() + paddingLine.length() <= targetSize) {
            content.append(paddingLine);
        }

        // Verify we're under the limit
        assertTrue(content.length() < maxFileSize,
                "Content should be under 10MB limit, but was: " + content.length());

        Files.writeString(configFile, content.toString());

        final StrictFilterConfiguration config = StrictFilterConfiguration.load(
                tempDir.toString(),
                tempDir
        );

        // Should load successfully when file is at or under the limit
        assertFalse(config.isEmpty(),
                "Configuration should load when file is at size limit");
    }

    @Test
    void testMalformedPropertiesFile(@TempDir Path tempDir) throws Exception {
        // Create a malformed properties file (not valid Java properties format)
        StrictPropertiesTestHelper.writeStrictProperties(tempDir, """
                This is not a valid properties file
                It has no key=value pairs
                Just random text
                """);

        final StrictFilterConfiguration config = StrictFilterConfiguration.load(
                tempDir.toString(),
                tempDir
        );

        // Should handle gracefully and return empty configuration
        assertTrue(config.isEmpty(),
                "Configuration should be empty when properties file is malformed");
    }

    @Test
    void testPropertyValueWithOnlyCommas(@TempDir Path tempDir) throws Exception {
        StrictPropertiesTestHelper.writeStrictProperties(tempDir, """
                repo.central.allow = ,,,
                """);

        final StrictFilterConfiguration config = StrictFilterConfiguration.load(
                tempDir.toString(),
                tempDir
        );

        // Should result in empty configuration for the repository
        assertTrue(config.isEmpty(),
                "Configuration should be empty when property value has only commas");
    }

    @Test
    void testPropertyValueWithTrailingComma(@TempDir Path tempDir) throws Exception {
        StrictPropertiesTestHelper.writeStrictProperties(tempDir, """
                repo.central.allow = org.graylog,org.apache,
                """);

        final StrictFilterConfiguration config = StrictFilterConfiguration.load(
                tempDir.toString(),
                tempDir
        );

        assertFalse(config.isEmpty(), "Configuration should not be empty");

        // Should properly trim empty strings from trailing comma
        final Artifact artifact = new DefaultArtifact("org.graylog:server:1.0");
        assertTrue(config.isArtifactAllowed("central", artifact),
                "Should handle trailing comma gracefully");
    }

    @Test
    void testPropertyValueWithLeadingComma(@TempDir Path tempDir) throws Exception {
        StrictPropertiesTestHelper.writeStrictProperties(tempDir, """
                repo.central.allow = ,org.graylog,org.apache
                """);

        final StrictFilterConfiguration config = StrictFilterConfiguration.load(
                tempDir.toString(),
                tempDir
        );

        assertFalse(config.isEmpty(), "Configuration should not be empty");

        // Should properly trim empty strings from leading comma
        final Artifact artifact = new DefaultArtifact("org.graylog:server:1.0");
        assertTrue(config.isArtifactAllowed("central", artifact),
                "Should handle leading comma gracefully");
    }

    @Test
    void testPropertyValueWithEmptyStringsBetweenCommas(@TempDir Path tempDir) throws Exception {
        StrictPropertiesTestHelper.writeStrictProperties(tempDir, """
                repo.central.allow = org.graylog,,org.apache
                """);

        final StrictFilterConfiguration config = StrictFilterConfiguration.load(
                tempDir.toString(),
                tempDir
        );

        assertFalse(config.isEmpty(), "Configuration should not be empty");

        // Should filter out empty strings
        final Artifact artifact1 = new DefaultArtifact("org.graylog:server:1.0");
        assertTrue(config.isArtifactAllowed("central", artifact1),
                "Should handle empty string between commas");

        final Artifact artifact2 = new DefaultArtifact("org.apache:commons:1.0");
        assertTrue(config.isArtifactAllowed("central", artifact2),
                "Should handle empty string between commas");
    }

    @Test
    void testMetadataWithNullArtifactId(@TempDir Path tempDir) throws Exception {
        StrictPropertiesTestHelper.writeStrictProperties(tempDir, "repo.central.allow = org.graylog\n");

        final StrictFilterConfiguration config = StrictFilterConfiguration.load(
                tempDir.toString(),
                tempDir
        );

        // Group-level metadata (has groupId but no artifactId)
        final Metadata metadata = new DefaultMetadata("org.graylog", "maven-metadata.xml",
                Metadata.Nature.RELEASE_OR_SNAPSHOT);

        assertTrue(config.isMetadataAllowed("central", metadata),
                "Group-level metadata should be allowed when groupId matches");
    }

    @Test
    void testMetadataWithEmptyArtifactId(@TempDir Path tempDir) throws Exception {
        StrictPropertiesTestHelper.writeStrictProperties(tempDir, "repo.central.allow = org.graylog\n");

        final StrictFilterConfiguration config = StrictFilterConfiguration.load(
                tempDir.toString(),
                tempDir
        );

        // Metadata with empty artifactId
        final Metadata metadata = new DefaultMetadata("org.graylog", "", "maven-metadata.xml",
                Metadata.Nature.RELEASE_OR_SNAPSHOT);

        assertTrue(config.isMetadataAllowed("central", metadata),
                "Metadata with empty artifactId should be allowed when groupId matches");
    }

    @Test
    void testMetadataWithCoordinatePattern(@TempDir Path tempDir) throws Exception {
        StrictPropertiesTestHelper.writeStrictProperties(tempDir, "repo.central.allow = org.graylog:server\n");

        final StrictFilterConfiguration config = StrictFilterConfiguration.load(
                tempDir.toString(),
                tempDir
        );

        // Artifact-level metadata with both groupId and artifactId
        final Metadata metadata = new DefaultMetadata("org.graylog", "server", "maven-metadata.xml",
                Metadata.Nature.RELEASE_OR_SNAPSHOT);

        assertTrue(config.isMetadataAllowed("central", metadata),
                "Artifact-level metadata should match coordinate pattern");
    }

    @Test
    void testPatternWithMultipleDots(@TempDir Path tempDir) throws Exception {
        StrictPropertiesTestHelper.writeStrictProperties(tempDir, "repo.central.allow = com.google.guava*\n");

        final StrictFilterConfiguration config = StrictFilterConfiguration.load(
                tempDir.toString(),
                tempDir
        );

        // Should match groupId starting with com.google.guava
        final Artifact artifact1 = new DefaultArtifact("com.google.guava:guava:30.0");
        assertTrue(config.isArtifactAllowed("central", artifact1),
                "Should match com.google.guava exactly");

        final Artifact artifact2 = new DefaultArtifact("com.google.guava.util:utils:1.0");
        assertTrue(config.isArtifactAllowed("central", artifact2),
                "Should match com.google.guava.util (sub-package)");

        // Should NOT match com.google (doesn't start with com.google.guava)
        final Artifact artifact3 = new DefaultArtifact("com.google:gson:2.8.0");
        assertFalse(config.isArtifactAllowed("central", artifact3),
                "Should not match com.google (missing .guava)");
    }

    @Test
    void testPatternWithSpecialCharactersInArtifactId(@TempDir Path tempDir) throws Exception {
        StrictPropertiesTestHelper.writeStrictProperties(tempDir, "repo.central.allow = com.test:lib-*\n");

        final StrictFilterConfiguration config = StrictFilterConfiguration.load(
                tempDir.toString(),
                tempDir
        );

        // Hyphen in artifactId pattern
        final Artifact artifact1 = new DefaultArtifact("com.test:lib-utils:1.0");
        assertTrue(config.isArtifactAllowed("central", artifact1),
                "Should match lib-utils with hyphen");

        final Artifact artifact2 = new DefaultArtifact("com.test:lib-core:1.0");
        assertTrue(config.isArtifactAllowed("central", artifact2),
                "Should match lib-core with hyphen");

        // Should NOT match without hyphen
        final Artifact artifact3 = new DefaultArtifact("com.test:libutils:1.0");
        assertFalse(config.isArtifactAllowed("central", artifact3),
                "Should not match libutils (missing hyphen)");
    }

    @Test
    void testPatternWithNumbers(@TempDir Path tempDir) throws Exception {
        StrictPropertiesTestHelper.writeStrictProperties(tempDir, "repo.central.allow = com.v1*\n");

        final StrictFilterConfiguration config = StrictFilterConfiguration.load(
                tempDir.toString(),
                tempDir
        );

        // Numbers in groupId pattern
        final Artifact artifact1 = new DefaultArtifact("com.v1:api:1.0");
        assertTrue(config.isArtifactAllowed("central", artifact1),
                "Should match com.v1 exactly");

        final Artifact artifact2 = new DefaultArtifact("com.v1.api:core:1.0");
        assertTrue(config.isArtifactAllowed("central", artifact2),
                "Should match com.v1.api (sub-package with number)");

        // Should NOT match com.v2
        final Artifact artifact3 = new DefaultArtifact("com.v2:api:1.0");
        assertFalse(config.isArtifactAllowed("central", artifact3),
                "Should not match com.v2 (different number)");
    }

    @Test
    void testCandidateListPrefersFirstExisting(@TempDir Path tempDir) throws Exception {
        final Path first = tempDir.resolve("first");
        final Path second = tempDir.resolve("second");
        StrictPropertiesTestHelper.writeStrictProperties(first, "repo.central.allow = org.first\n");
        StrictPropertiesTestHelper.writeStrictProperties(second, "repo.central.allow = org.second\n");

        final StrictFilterConfiguration config = StrictFilterConfiguration.loadFromCandidates(List.of(first, second));

        assertEquals(first, config.getBasedir(), "First existing candidate should be selected");
        assertFalse(config.isEmpty(), "Configuration should not be empty");
    }

    @Test
    void testCandidateListFallsBackToLaterCandidate(@TempDir Path tempDir) throws Exception {
        final Path first = tempDir.resolve("first");   // no strict.properties created here
        final Path second = tempDir.resolve("second");
        StrictPropertiesTestHelper.writeStrictProperties(second, "repo.central.allow = org.second\n");

        final StrictFilterConfiguration config = StrictFilterConfiguration.loadFromCandidates(List.of(first, second));

        assertEquals(second, config.getBasedir(), "Should fall back to the candidate that has a config file");
        assertFalse(config.isEmpty(), "Configuration should not be empty");
    }

    @Test
    void testCandidateListNoneExistReportsLast(@TempDir Path tempDir) {
        final Path first = tempDir.resolve("first");
        final Path second = tempDir.resolve("second");

        final StrictFilterConfiguration config = StrictFilterConfiguration.loadFromCandidates(List.of(first, second));

        assertTrue(config.isEmpty(), "Configuration should be empty when no candidate has a config file");
        assertEquals(second, config.getBasedir(), "Should report the last candidate when none exist (fail-secure default)");
    }

    @Test
    void testCandidatePresentButEmptyFileIsAuthoritative(@TempDir Path tempDir) throws Exception {
        final Path first = tempDir.resolve("first");
        final Path second = tempDir.resolve("second");
        StrictPropertiesTestHelper.writeStrictProperties(first, "# no rules here\n");        // exists but empty
        StrictPropertiesTestHelper.writeStrictProperties(second, "repo.central.allow = org.second\n");

        final StrictFilterConfiguration config = StrictFilterConfiguration.loadFromCandidates(List.of(first, second));

        assertEquals(first, config.getBasedir(), "A present (even empty) file is authoritative; no fallback");
        assertTrue(config.isEmpty(), "Empty project file yields no rules (fail-secure)");
    }

    @Test
    void testCandidateListEmptyThrows() {
        assertThrows(IllegalArgumentException.class, () -> StrictFilterConfiguration.loadFromCandidates(List.of()),
                "Empty candidate list must be rejected");
    }
}
