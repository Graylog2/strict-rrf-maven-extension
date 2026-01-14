package org.graylog.maven.resolver.filter.strict;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.metadata.DefaultMetadata;
import org.eclipse.aether.metadata.Metadata;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link RepositoryRule}.
 */
class RepositoryRuleTest {

    @Test
    void testEmptyAllowPatternsDenyAll() {
        final RepositoryRule rule = new RepositoryRule(Set.of(), Set.of());

        final Artifact artifact = new DefaultArtifact("org.graylog:server:1.0");

        assertFalse(rule.isArtifactAllowed(artifact),
                "Should deny all when no allow patterns specified");
    }

    @Test
    void testExactGroupIdMatch() {
        final RepositoryRule rule = new RepositoryRule(
                Set.of("org.graylog"),
                Set.of()
        );

        // Exact match should work
        final Artifact exactMatch = new DefaultArtifact("org.graylog:server:1.0");
        assertTrue(rule.isArtifactAllowed(exactMatch),
                "Should allow exact groupId match");

        // Sub-package should NOT match without wildcard
        final Artifact subPackage = new DefaultArtifact("org.graylog.plugin:api:1.0");
        assertFalse(rule.isArtifactAllowed(subPackage),
                "Should not allow sub-package without wildcard");
    }

    @Test
    void testWildcardGroupIdMatch() {
        final RepositoryRule rule = new RepositoryRule(
                Set.of("org.graylog*"),
                Set.of()
        );

        // Exact match
        final Artifact exactMatch = new DefaultArtifact("org.graylog:server:1.0");
        assertTrue(rule.isArtifactAllowed(exactMatch),
                "Should allow exact match with wildcard pattern");

        // Sub-package match
        final Artifact subPackage = new DefaultArtifact("org.graylog.plugin:api:1.0");
        assertTrue(rule.isArtifactAllowed(subPackage),
                "Should allow sub-package with wildcard pattern");

        // Different groupId
        final Artifact different = new DefaultArtifact("org.apache:commons:1.0");
        assertFalse(rule.isArtifactAllowed(different),
                "Should not allow different groupId");
    }

    @Test
    void testDenyOverridesAllow() {
        final RepositoryRule rule = new RepositoryRule(
                Set.of("org.graylog*"),
                Set.of("org.graylog.internal*")
        );

        // Allowed
        final Artifact allowed = new DefaultArtifact("org.graylog:server:1.0");
        assertTrue(rule.isArtifactAllowed(allowed),
                "Should allow non-internal artifact");

        // Denied
        final Artifact denied = new DefaultArtifact("org.graylog.internal:utils:1.0");
        assertFalse(rule.isArtifactAllowed(denied),
                "Should deny internal artifact (deny overrides allow)");
    }

    @Test
    void testCoordinatePatternWildcardArtifactId() {
        final RepositoryRule rule = new RepositoryRule(
                Set.of("com.opensaml:*"),
                Set.of()
        );

        // Any artifact in groupId should match
        final Artifact artifact1 = new DefaultArtifact("com.opensaml:opensaml-core:4.0.0");
        assertTrue(rule.isArtifactAllowed(artifact1),
                "Should allow any artifact in com.opensaml");

        final Artifact artifact2 = new DefaultArtifact("com.opensaml:opensaml-saml:4.0.0");
        assertTrue(rule.isArtifactAllowed(artifact2),
                "Should allow any artifact in com.opensaml");

        // Different groupId should not match
        final Artifact different = new DefaultArtifact("com.other:something:1.0");
        assertFalse(rule.isArtifactAllowed(different),
                "Should not allow different groupId");
    }

    @Test
    void testCoordinatePatternPrefixMatch() {
        final RepositoryRule rule = new RepositoryRule(
                Set.of("com.company:test-*"),
                Set.of()
        );

        // Matching prefix
        final Artifact match1 = new DefaultArtifact("com.company:test-utils:1.0");
        assertTrue(rule.isArtifactAllowed(match1),
                "Should allow test-utils");

        final Artifact match2 = new DefaultArtifact("com.company:test-core:1.0");
        assertTrue(rule.isArtifactAllowed(match2),
                "Should allow test-core");

        // Non-matching artifactId
        final Artifact noMatch = new DefaultArtifact("com.company:production-lib:1.0");
        assertFalse(rule.isArtifactAllowed(noMatch),
                "Should not allow production-lib");
    }

    @Test
    void testCoordinatePatternExactMatch() {
        final RepositoryRule rule = new RepositoryRule(
                Set.of("com.google:guava"),
                Set.of()
        );

        // Exact match
        final Artifact exactMatch = new DefaultArtifact("com.google:guava:30.0");
        assertTrue(rule.isArtifactAllowed(exactMatch),
                "Should allow exact coordinate match");

        // Different artifactId in same groupId
        final Artifact different = new DefaultArtifact("com.google:gson:2.8.0");
        assertFalse(rule.isArtifactAllowed(different),
                "Should not allow different artifactId");
    }

    @Test
    void testCoordinateDenyPattern() {
        final RepositoryRule rule = new RepositoryRule(
                Set.of("com.opensaml:*"),
                Set.of("com.opensaml:*-internal")
        );

        // Allowed
        final Artifact allowed = new DefaultArtifact("com.opensaml:opensaml-core:4.0.0");
        assertTrue(rule.isArtifactAllowed(allowed),
                "Should allow opensaml-core");

        // Denied by suffix
        final Artifact denied1 = new DefaultArtifact("com.opensaml:utils-internal:1.0");
        assertFalse(rule.isArtifactAllowed(denied1),
                "Should deny utils-internal");

        final Artifact denied2 = new DefaultArtifact("com.opensaml:test-internal:1.0");
        assertFalse(rule.isArtifactAllowed(denied2),
                "Should deny test-internal");
    }

    @Test
    void testWildcardMatchesEverything() {
        final RepositoryRule rule = new RepositoryRule(
                Set.of("*"),
                Set.of()
        );

        final Artifact artifact1 = new DefaultArtifact("org.graylog:server:1.0");
        assertTrue(rule.isArtifactAllowed(artifact1),
                "Wildcard should match everything");

        final Artifact artifact2 = new DefaultArtifact("com.example:app:2.0");
        assertTrue(rule.isArtifactAllowed(artifact2),
                "Wildcard should match everything");
    }

    @Test
    void testMultipleAllowPatterns() {
        final RepositoryRule rule = new RepositoryRule(
                Set.of("org.graylog", "org.apache.commons", "com.google:guava"),
                Set.of()
        );

        // First pattern (exact groupId)
        final Artifact match1 = new DefaultArtifact("org.graylog:server:1.0");
        assertTrue(rule.isArtifactAllowed(match1),
                "Should match first pattern");

        // Second pattern (exact groupId)
        final Artifact match2 = new DefaultArtifact("org.apache.commons:commons-lang3:3.0");
        assertTrue(rule.isArtifactAllowed(match2),
                "Should match second pattern");

        // Third pattern (exact coordinate)
        final Artifact match3 = new DefaultArtifact("com.google:guava:30.0");
        assertTrue(rule.isArtifactAllowed(match3),
                "Should match third pattern");

        // No match
        final Artifact noMatch = new DefaultArtifact("com.example:test:1.0");
        assertFalse(rule.isArtifactAllowed(noMatch),
                "Should not match any pattern");
    }

    @Test
    void testMultipleDenyPatterns() {
        final RepositoryRule rule = new RepositoryRule(
                Set.of("org.graylog*"),
                Set.of("org.graylog.internal*", "org.graylog.test*")
        );

        // Allowed
        final Artifact allowed = new DefaultArtifact("org.graylog:server:1.0");
        assertTrue(rule.isArtifactAllowed(allowed),
                "Should allow non-internal, non-test artifact");

        // Denied by first pattern
        final Artifact denied1 = new DefaultArtifact("org.graylog.internal:utils:1.0");
        assertFalse(rule.isArtifactAllowed(denied1),
                "Should deny internal artifact");

        // Denied by second pattern
        final Artifact denied2 = new DefaultArtifact("org.graylog.test:fixtures:1.0");
        assertFalse(rule.isArtifactAllowed(denied2),
                "Should deny test artifact");
    }

    @Test
    void testMetadataWithGroupIdOnly() {
        final RepositoryRule rule = new RepositoryRule(
                Set.of("org.graylog"),
                Set.of()
        );

        // Group-level metadata (no artifactId)
        final Metadata metadata = new DefaultMetadata("org.graylog", "maven-metadata.xml",
                Metadata.Nature.RELEASE_OR_SNAPSHOT);

        assertTrue(rule.isMetadataAllowed(metadata),
                "Should allow group-level metadata when groupId matches");
    }

    @Test
    void testMetadataWithGroupIdAndArtifactId() {
        final RepositoryRule rule = new RepositoryRule(
                Set.of("org.graylog:server"),
                Set.of()
        );

        // Artifact-level metadata
        final Metadata metadata = new DefaultMetadata("org.graylog", "server", "maven-metadata.xml",
                Metadata.Nature.RELEASE_OR_SNAPSHOT);

        assertTrue(rule.isMetadataAllowed(metadata),
                "Should allow artifact-level metadata when coordinate matches");
    }

    @Test
    void testMetadataGroupOnlyMatchesCoordinatePattern() {
        final RepositoryRule rule = new RepositoryRule(
                Set.of("org.graylog:server"),
                Set.of()
        );

        // Group-level metadata should match just the groupId part of coordinate pattern
        final Metadata groupMetadata = new DefaultMetadata("org.graylog", "maven-metadata.xml",
                Metadata.Nature.RELEASE_OR_SNAPSHOT);

        assertTrue(rule.isMetadataAllowed(groupMetadata),
                "Group-level metadata should match groupId part of coordinate pattern");
    }

    @Test
    void testMetadataWithWildcardPattern() {
        final RepositoryRule rule = new RepositoryRule(
                Set.of("org.graylog*"),
                Set.of()
        );

        // Metadata with matching groupId
        final Metadata metadata1 = new DefaultMetadata("org.graylog", "maven-metadata.xml",
                Metadata.Nature.RELEASE_OR_SNAPSHOT);
        assertTrue(rule.isMetadataAllowed(metadata1),
                "Should allow metadata with exact groupId");

        // Metadata with sub-package groupId
        final Metadata metadata2 = new DefaultMetadata("org.graylog.plugin", "maven-metadata.xml",
                Metadata.Nature.RELEASE_OR_SNAPSHOT);
        assertTrue(rule.isMetadataAllowed(metadata2),
                "Should allow metadata with sub-package groupId");
    }

    @Test
    void testMetadataDenied() {
        final RepositoryRule rule = new RepositoryRule(
                Set.of("org.graylog*"),
                Set.of("org.graylog.internal*")
        );

        // Allowed metadata
        final Metadata allowed = new DefaultMetadata("org.graylog", "maven-metadata.xml",
                Metadata.Nature.RELEASE_OR_SNAPSHOT);
        assertTrue(rule.isMetadataAllowed(allowed),
                "Should allow non-internal metadata");

        // Denied metadata
        final Metadata denied = new DefaultMetadata("org.graylog.internal", "maven-metadata.xml",
                Metadata.Nature.RELEASE_OR_SNAPSHOT);
        assertFalse(rule.isMetadataAllowed(denied),
                "Should deny internal metadata");
    }

    @Test
    void testCaseSensitiveMatching() {
        final RepositoryRule rule = new RepositoryRule(
                Set.of("org.graylog"),
                Set.of()
        );

        // Exact case match
        final Artifact exactCase = new DefaultArtifact("org.graylog:server:1.0");
        assertTrue(rule.isArtifactAllowed(exactCase),
                "Should match with exact case");

        // Different case should NOT match (case-sensitive)
        final Artifact differentCase = new DefaultArtifact("org.Graylog:server:1.0");
        assertFalse(rule.isArtifactAllowed(differentCase),
                "Should be case-sensitive");
    }

    @Test
    void testEmptyDenyPatterns() {
        final RepositoryRule rule = new RepositoryRule(
                Set.of("org.graylog*"),
                Set.of()
        );

        final Artifact artifact = new DefaultArtifact("org.graylog:server:1.0");
        assertTrue(rule.isArtifactAllowed(artifact),
                "Should allow when no deny patterns specified");
    }

    @Test
    void testBothAllowAndDenyEmpty() {
        final RepositoryRule rule = new RepositoryRule(Set.of(), Set.of());

        final Artifact artifact = new DefaultArtifact("org.graylog:server:1.0");
        assertFalse(rule.isArtifactAllowed(artifact),
                "Should deny all when both allow and deny patterns are empty");
    }
}
