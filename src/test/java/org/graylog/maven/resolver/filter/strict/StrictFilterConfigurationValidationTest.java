package org.graylog.maven.resolver.filter.strict;

import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.metadata.DefaultMetadata;
import org.eclipse.aether.metadata.Metadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the fail-secure validation in {@link StrictFilterConfiguration}: ambiguous or
 * invalid rules must block the repository instead of being silently ignored.
 */
class StrictFilterConfigurationValidationTest {

    @Test
    void testDuplicateDenyKeyBlocksRepository(@TempDir Path tempDir) throws Exception {
        StrictPropertiesTestHelper.writeStrictProperties(tempDir, """
                repo.central.allow = *
                repo.central.deny = com.google.guava
                repo.central.deny = org.evil
                repo.other.allow = org.other
                """);

        final StrictFilterConfiguration config = StrictFilterConfiguration.load(tempDir.toString(), tempDir);

        assertFalse(config.isArtifactAllowed("central", new DefaultArtifact("org.graylog:server:1.0")),
                "Duplicate key must block the whole repository instead of keeping only the last line");
        assertFalse(config.isMetadataAllowed("central", new DefaultMetadata("maven-metadata.xml",
                        Metadata.Nature.RELEASE_OR_SNAPSHOT)),
                "Blocked repository must also reject repository-level metadata");
        assertTrue(config.isArtifactAllowed("other", new DefaultArtifact("org.other:lib:1.0")),
                "Other repositories must be unaffected");
    }

    @Test
    void testDuplicateAllowKeyBlocksRepository(@TempDir Path tempDir) throws Exception {
        StrictPropertiesTestHelper.writeStrictProperties(tempDir, """
                repo.central.allow = org.graylog
                repo.central.allow = org.apache
                """);

        final StrictFilterConfiguration config = StrictFilterConfiguration.load(tempDir.toString(), tempDir);

        assertFalse(config.isArtifactAllowed("central", new DefaultArtifact("org.graylog:server:1.0")),
                "Duplicate allow key must block the repository");
        assertFalse(config.isArtifactAllowed("central", new DefaultArtifact("org.apache:lib:1.0")),
                "Duplicate allow key must block the repository");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "com.google.guava:guava:31.0",
            "org.foo?",
            "org.foo?*",
            "%regex[org.graylog.*]",
            "%ant[org/graylog/**]",
            "org.foo bar",
            "com.google:",
            ":guava",
            "org/graylog"
    })
    void testInvalidDenyPatternBlocksRepository(String pattern, @TempDir Path tempDir) throws Exception {
        StrictPropertiesTestHelper.writeStrictProperties(tempDir, """
                repo.central.allow = org.graylog,com.google*
                repo.central.deny = %s
                repo.other.allow = org.other
                """.formatted(pattern));

        final StrictFilterConfiguration config = StrictFilterConfiguration.load(tempDir.toString(), tempDir);

        assertFalse(config.isArtifactAllowed("central", new DefaultArtifact("org.graylog:server:1.0")),
                "Invalid deny pattern '" + pattern + "' must block the whole repository");
        assertTrue(config.isArtifactAllowed("other", new DefaultArtifact("org.other:lib:1.0")),
                "Other repositories must be unaffected");
    }

    @Test
    void testInvalidAllowPatternBlocksRepository(@TempDir Path tempDir) throws Exception {
        StrictPropertiesTestHelper.writeStrictProperties(tempDir, """
                repo.central.allow = org.graylog,org.apache:commons:3.0
                """);

        final StrictFilterConfiguration config = StrictFilterConfiguration.load(tempDir.toString(), tempDir);

        assertFalse(config.isArtifactAllowed("central", new DefaultArtifact("org.graylog:server:1.0")),
                "Invalid allow pattern must block the whole repository, including its valid patterns");
    }

    @Test
    void testBlankDenyKeepsAllowRules(@TempDir Path tempDir) throws Exception {
        StrictPropertiesTestHelper.writeStrictProperties(tempDir, """
                repo.central.allow = org.graylog
                repo.central.deny =
                """);

        final StrictFilterConfiguration config = StrictFilterConfiguration.load(tempDir.toString(), tempDir);

        assertTrue(config.isArtifactAllowed("central", new DefaultArtifact("org.graylog:server:1.0")),
                "A blank deny rule is skipped (with a warning); allow rules still apply");
    }

    @Test
    void testCatchAllAllowIsLoaded(@TempDir Path tempDir) throws Exception {
        StrictPropertiesTestHelper.writeStrictProperties(tempDir, """
                repo.central.allow = *
                repo.central.deny = com.google.guava
                """);

        final StrictFilterConfiguration config = StrictFilterConfiguration.load(tempDir.toString(), tempDir);

        assertTrue(config.isArtifactAllowed("central", new DefaultArtifact("org.graylog:server:1.0")),
                "Catch-all allow is a valid rule");
        assertFalse(config.isArtifactAllowed("central", new DefaultArtifact("com.google.guava:guava:31.0")),
                "Deny still applies under a catch-all allow");
    }
}
