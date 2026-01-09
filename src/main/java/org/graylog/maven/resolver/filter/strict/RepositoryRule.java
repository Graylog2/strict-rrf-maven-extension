package org.graylog.maven.resolver.filter.strict;

import org.apache.maven.shared.utils.io.SelectorUtils;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.metadata.Metadata;

import java.util.Collections;
import java.util.Set;

/**
 * Repository filtering rule with allow and deny patterns.
 */
class RepositoryRule {
    private final Set<String> allowPatterns;
    private final Set<String> denyPatterns;

    RepositoryRule(Set<String> allowPatterns, Set<String> denyPatterns) {
        this.allowPatterns = Collections.unmodifiableSet(allowPatterns);
        this.denyPatterns = Collections.unmodifiableSet(denyPatterns);
    }

    boolean isArtifactAllowed(Artifact artifact) {
        return isAllowed(artifact.getGroupId(), artifact.getArtifactId(), RepositoryRule::matchesPattern);
    }

    boolean isMetadataAllowed(Metadata metadata) {
        return isAllowed(metadata.getGroupId(), metadata.getArtifactId(), RepositoryRule::matchesMetadataPattern);
    }

    /**
     * Common pattern matching logic for both artifacts and metadata.
     * Uses the provided matcher function to check patterns.
     *
     * @param groupId    the groupId to check
     * @param artifactId the artifactId to check (may be null for metadata)
     * @param matcher    the pattern matching function to use
     * @return true if allowed, false otherwise
     */
    private boolean isAllowed(String groupId, String artifactId, PatternMatcher matcher) {
        // If no allow patterns specified, deny by default
        if (allowPatterns.isEmpty()) {
            return false;
        }

        // Check if matches any allow pattern
        final boolean allowed = allowPatterns.stream()
                .anyMatch(allowPattern -> matcher.matches(groupId, artifactId, allowPattern));

        // Check deny patterns (deny overrides allow)
        return allowed && denyPatterns.stream()
                .noneMatch(denyPattern -> matcher.matches(groupId, artifactId, denyPattern));
    }

    /**
     * Functional interface for pattern matching.
     */
    @FunctionalInterface
    private interface PatternMatcher {
        boolean matches(String groupId, String artifactId, String pattern);
    }

    /**
     * Matches metadata against a glob pattern.
     * Metadata can have groupId only (group-level) or both groupId and artifactId (artifact-level).
     */
    private static boolean matchesMetadataPattern(String groupId, String artifactId, String pattern) {
        // Metadata without artifactId should only match the groupId part of coordinate patterns
        final boolean allowPartialMatch = artifactId == null || artifactId.isEmpty();
        return matchesCoordinatePattern(groupId, artifactId, pattern, allowPartialMatch);
    }

    /**
     * Matches an artifact against a glob pattern.
     * Supports * as wildcard and groupId:artifactId coordinate patterns.
     * <p>
     * Examples:
     * - "com.google" matches only groupId "com.google" (exact match)
     * - "com.google*" matches groupId "com.google", "com.googlefoo", "com.google.foo"
     * - "com.google.*" matches groupId "com.google.foo" but not "com.google"
     * - "com.opensaml:*" matches any artifact in groupId "com.opensaml"
     * - "com.foobar:test-*" matches artifacts starting with "test-" in groupId "com.foobar"
     * - "*" matches everything
     */
    private static boolean matchesPattern(String groupId, String artifactId, String pattern) {
        return matchesCoordinatePattern(groupId, artifactId, pattern, false);
    }

    /**
     * Common coordinate pattern matching logic.
     *
     * @param groupId           the groupId to match
     * @param artifactId        the artifactId to match (may be null)
     * @param pattern           the pattern to match against
     * @param allowPartialMatch if true, allows matching only groupId when artifactId is null
     * @return true if the coordinate matches the pattern
     */
    private static boolean matchesCoordinatePattern(String groupId,
                                                    String artifactId,
                                                    String pattern,
                                                    boolean allowPartialMatch) {
        if ("*".equals(pattern)) {
            return true;
        }

        // Check if pattern includes artifactId (contains ':')
        if (pattern.contains(":")) {
            // If we allow partial match and artifactId is null, match only groupId
            if (allowPartialMatch && (artifactId == null || artifactId.isEmpty())) {
                final String groupIdPattern = pattern.split(":", 2)[0];
                return matchesGlobPattern(groupId, groupIdPattern);
            }

            // Full coordinate matching
            final String[] parts = pattern.split(":", 2);
            final String groupIdPattern = parts[0];
            final String artifactIdPattern = parts.length > 1 ? parts[1] : "*";

            return matchesGlobPattern(groupId, groupIdPattern)
                    && matchesGlobPattern(artifactId, artifactIdPattern);
        }

        // GroupId-only pattern
        return matchesGlobPattern(groupId, pattern);
    }

    /**
     * Matches a string against a glob pattern using Maven's SelectorUtils.
     * SelectorUtils provides battle-tested glob matching with * wildcard.
     * Uses . as the path separator to match Maven groupId/artifactId structure.
     *
     * <p>Note: SelectorUtils is deprecated but still the most reliable option for Maven glob matching.
     * No clear replacement has been provided yet. Alternative (maven-common-artifact-filters) uses
     * incompatible Maven Artifact API instead of Aether/Resolver API.
     */
    @SuppressWarnings("deprecation")
    private static boolean matchesGlobPattern(String value, String pattern) {
        if ("*".equals(pattern)) {
            return true;
        }

        if (!pattern.contains("*")) {
            // No wildcard - exact match only
            return value.equals(pattern);
        }

        // Use SelectorUtils for glob pattern matching
        // SelectorUtils.matchPath treats both / and . as path separators
        // which is perfect for matching Maven coordinates like "com.google.foo"
        return SelectorUtils.matchPath(pattern, value, true);
    }
}
