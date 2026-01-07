package org.graylog.maven.resolver.filter.strict;

import org.apache.maven.shared.utils.io.SelectorUtils;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.metadata.Metadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Configuration for the strict remote repository filter.
 * Loads per-repository filtering rules from a single properties file: strict.properties
 *
 * <p>Configuration format supports both groupId-only and full coordinate (groupId:artifactId) patterns:
 * <pre>
 * # Comments start with #
 *
 * # GroupId-only patterns - match all artifacts in the groupId
 * repo.central.allow = org.graylog,org.apache.maven,org.springframework
 * repo.central.deny = org.apache.maven.internal*
 *
 * # Coordinate patterns - match specific artifacts
 * repo.shibboleth.allow = org.opensaml:*,net.shibboleth:*
 * repo.shibboleth.deny = org.opensaml:*-internal
 *
 * # Mixed patterns - combine groupId and coordinate patterns
 * repo.test.allow = org.junit,com.google:guava,com.company:test-*
 * </pre>
 *
 * <p>Pattern matching rules:
 * <ul>
 * <li>GroupId-only: {@code org.graylog} matches {@code org.graylog:*} and {@code org.graylog.plugin:*}</li>
 * <li>Wildcard groupId: {@code com.google*} matches {@code com.google:*}, {@code com.googlecode:*}</li>
 * <li>Coordinate with wildcard: {@code com.opensaml:*} matches all artifacts in {@code com.opensaml}</li>
 * <li>Coordinate with pattern: {@code com.company:test-*} matches {@code com.company:test-utils}, etc.</li>
 * <li>Exact coordinate: {@code com.google:guava} matches only {@code com.google:guava}</li>
 * </ul>
 *
 * <p>By default, everything is denied unless explicitly allowed.
 * Deny rules override allow rules. Supports glob patterns with * wildcard.
 */
public class StrictFilterConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(StrictFilterConfiguration.class);
    private static final String CONFIG_FILE_NAME = "strict.properties";
    private static final String REPO_PREFIX = "repo.";
    private static final String ALLOW_SUFFIX = ".allow";
    private static final String DENY_SUFFIX = ".deny";

    private final Path basedir;
    private final Map<String, RepositoryRule> repositoryRules; // repositoryId -> RepositoryRule

    private StrictFilterConfiguration(Path basedir, Map<String, RepositoryRule> repositoryRules) {
        this.basedir = basedir;
        this.repositoryRules = repositoryRules;
    }

    /**
     * Repository filtering rule with allow and deny patterns.
     */
    private static class RepositoryRule {
        private final Set<String> allowPatterns;
        private final Set<String> denyPatterns;

        RepositoryRule(Set<String> allowPatterns, Set<String> denyPatterns) {
            this.allowPatterns = allowPatterns;
            this.denyPatterns = denyPatterns;
        }

        boolean isArtifactAllowed(Artifact artifact) {
            // If no allow patterns specified, deny by default
            if (allowPatterns.isEmpty()) {
                return false;
            }

            final String groupId = artifact.getGroupId();
            final String artifactId = artifact.getArtifactId();

            // Check if artifact matches any allow pattern
            boolean allowed = false;
            for (final String allowPattern : allowPatterns) {
                if (matchesPattern(groupId, artifactId, allowPattern)) {
                    allowed = true;
                    break;
                }
            }

            // If not in allow list, deny
            if (!allowed) {
                return false;
            }

            // Check deny patterns (deny overrides allow)
            for (final String denyPattern : denyPatterns) {
                if (matchesPattern(groupId, artifactId, denyPattern)) {
                    return false;
                }
            }

            // Allowed and not denied
            return true;
        }

        boolean isMetadataAllowed(Metadata metadata) {
            // If no allow patterns specified, deny by default
            if (allowPatterns.isEmpty()) {
                return false;
            }

            final String groupId = metadata.getGroupId();
            final String artifactId = metadata.getArtifactId();

            // Check if metadata matches any allow pattern
            boolean allowed = false;
            for (final String allowPattern : allowPatterns) {
                if (matchesMetadataPattern(groupId, artifactId, allowPattern)) {
                    allowed = true;
                    break;
                }
            }

            // If not in allow list, deny
            if (!allowed) {
                return false;
            }

            // Check deny patterns (deny overrides allow)
            for (final String denyPattern : denyPatterns) {
                if (matchesMetadataPattern(groupId, artifactId, denyPattern)) {
                    return false;
                }
            }

            // Allowed and not denied
            return true;
        }

        /**
         * Matches metadata against a glob pattern.
         * Metadata can have groupId only (group-level) or both groupId and artifactId (artifact-level).
         */
        private static boolean matchesMetadataPattern(String groupId, String artifactId, String pattern) {
            if ("*".equals(pattern)) {
                return true;
            }

            // Check if pattern includes artifactId (contains ':')
            if (pattern.contains(":")) {
                // If pattern has artifactId but metadata doesn't, check only groupId
                if (artifactId == null || artifactId.isEmpty()) {
                    final String groupIdPattern = pattern.split(":", 2)[0];
                    return matchesGlobPattern(groupId, groupIdPattern);
                }

                // Both metadata and pattern have artifactId - match both
                final String[] parts = pattern.split(":", 2);
                final String groupIdPattern = parts[0];
                final String artifactIdPattern = parts.length > 1 ? parts[1] : "*";

                return matchesGlobPattern(groupId, groupIdPattern)
                        && matchesGlobPattern(artifactId, artifactIdPattern);
            }

            // Legacy groupId-only pattern
            return matchesGlobPattern(groupId, pattern);
        }

        /**
         * Matches an artifact against a glob pattern.
         * Supports * as wildcard and groupId:artifactId coordinate patterns.
         * <p>
         * Examples:
         * - "com.google" matches groupId "com.google" or "com.google.foo"
         * - "com.google*" matches groupId "com.google", "com.googlefoo", "com.google.foo"
         * - "com.google.*" matches groupId "com.google.foo" but not "com.google"
         * - "com.opensaml:*" matches any artifact in groupId "com.opensaml"
         * - "com.foobar:test-*" matches artifacts starting with "test-" in groupId "com.foobar"
         * - "*" matches everything
         */
        private static boolean matchesPattern(String groupId, String artifactId, String pattern) {
            if ("*".equals(pattern)) {
                return true;
            }

            // Check if pattern includes artifactId (contains ':')
            if (pattern.contains(":")) {
                final String[] parts = pattern.split(":", 2);
                final String groupIdPattern = parts[0];
                final String artifactIdPattern = parts.length > 1 ? parts[1] : "*";

                // Both groupId and artifactId must match
                return matchesGlobPattern(groupId, groupIdPattern)
                        && matchesGlobPattern(artifactId, artifactIdPattern);
            }

            // Legacy groupId-only pattern
            return matchesGlobPattern(groupId, pattern);
        }

        /**
         * Matches a string against a glob pattern using Maven's SelectorUtils.
         * SelectorUtils provides battle-tested glob matching with * and ? wildcards.
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

            if (!pattern.contains("*") && !pattern.contains("?")) {
                // No wildcard - exact match or prefix match (for groupId legacy behavior)
                return value.equals(pattern) || value.startsWith(pattern + ".");
            }

            // Use SelectorUtils for glob pattern matching
            // SelectorUtils.matchPath treats both / and . as path separators
            // which is perfect for matching Maven coordinates like "com.google.foo"
            return SelectorUtils.matchPath(pattern, value, true);
        }
    }

    /**
     * Loads configuration from the specified base directory.
     *
     * @param basedirConfig    the base directory configuration (can be relative or absolute)
     * @param localRepoBasedir the local repository base directory for resolving relative paths
     * @return a configuration instance
     */
    public static StrictFilterConfiguration load(String basedirConfig, Path localRepoBasedir) {
        final Path basedir = resolveBasedir(basedirConfig, localRepoBasedir);
        final Path configFile = basedir.resolve(CONFIG_FILE_NAME);

        if (!Files.exists(configFile) || !Files.isRegularFile(configFile)) {
            logger.debug("Configuration file does not exist: {}", configFile);
            return empty(basedir);
        }

        final Map<String, RepositoryRule> repositoryRules = new HashMap<>();
        loadPropertiesFile(configFile, repositoryRules);

        return new StrictFilterConfiguration(basedir, repositoryRules);
    }

    private static void loadPropertiesFile(Path file, Map<String, RepositoryRule> repositoryRules) {
        logger.debug("Loading configuration from: {}", file);

        final Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(file)) {
            properties.load(input);
        } catch (IOException e) {
            logger.warn("Failed to read configuration file: {}", file, e);
            return;
        }

        // First pass: collect all repository IDs
        final Map<String, Set<String>> allowPatterns = new HashMap<>();
        final Map<String, Set<String>> denyPatterns = new HashMap<>();

        for (final String key : properties.stringPropertyNames()) {
            // Check if key starts with "repo."
            if (!key.startsWith(REPO_PREFIX)) {
                logger.warn("Skipping invalid property '{}' in {}: key must start with 'repo.'", key, file);
                continue;
            }

            final String remainder = key.substring(REPO_PREFIX.length());

            // Extract repository ID and rule type (.allow or .deny)
            final String repoId;
            final boolean isAllow;

            if (remainder.endsWith(ALLOW_SUFFIX)) {
                repoId = remainder.substring(0, remainder.length() - ALLOW_SUFFIX.length());
                isAllow = true;
            } else if (remainder.endsWith(DENY_SUFFIX)) {
                repoId = remainder.substring(0, remainder.length() - DENY_SUFFIX.length());
                isAllow = false;
            } else {
                logger.warn("Skipping invalid property '{}' in {}: must end with '.allow' or '.deny'", key, file);
                continue;
            }

            if (repoId.isEmpty()) {
                logger.warn("Skipping invalid property '{}' in {}: empty repository ID", key, file);
                continue;
            }

            final String value = properties.getProperty(key);
            if (value == null || value.isBlank()) {
                logger.debug("Empty value for property '{}', skipping", key);
                continue;
            }

            // Parse comma-separated patterns
            final Set<String> patterns = parsePatterns(value);

            if (!patterns.isEmpty()) {
                if (isAllow) {
                    allowPatterns.computeIfAbsent(repoId, k -> new HashSet<>()).addAll(patterns);
                    logger.info("Loaded {} allow patterns for repository '{}': {}", patterns.size(), repoId, patterns);
                } else {
                    denyPatterns.computeIfAbsent(repoId, k -> new HashSet<>()).addAll(patterns);
                    logger.info("Loaded {} deny patterns for repository '{}': {}", patterns.size(), repoId, patterns);
                }
            }
        }

        // Create RepositoryRule for each repository
        final Set<String> allRepoIds = new HashSet<>();
        allRepoIds.addAll(allowPatterns.keySet());
        allRepoIds.addAll(denyPatterns.keySet());

        for (final String repoId : allRepoIds) {
            final Set<String> allow = allowPatterns.getOrDefault(repoId, Collections.emptySet());
            final Set<String> deny = denyPatterns.getOrDefault(repoId, Collections.emptySet());

            repositoryRules.put(repoId, new RepositoryRule(allow, deny));
            logger.info("Created rule for repository '{}': {} allow patterns, {} deny patterns",
                    repoId, allow.size(), deny.size());
        }

        if (repositoryRules.isEmpty()) {
            logger.debug("No repository rules loaded from: {}", file);
        }
    }

    private static Set<String> parsePatterns(String value) {
        final Set<String> patterns = new HashSet<>();
        for (String pattern : value.split(",")) {
            pattern = pattern.trim();
            if (!pattern.isEmpty()) {
                patterns.add(pattern);
            }
        }
        return patterns;
    }

    private static Path resolveBasedir(String basedirConfig, Path localRepoBasedir) {
        final Path path = Paths.get(basedirConfig);
        if (path.isAbsolute()) {
            return path;
        }
        // Relative path: resolve from local repository base directory
        return localRepoBasedir.resolve(basedirConfig);
    }

    private static StrictFilterConfiguration empty(Path basedir) {
        return new StrictFilterConfiguration(basedir, Collections.emptyMap());
    }

    /**
     * Checks if this configuration is empty (no rules loaded).
     *
     * @return true if no configuration was loaded
     */
    public boolean isEmpty() {
        return repositoryRules.isEmpty();
    }

    /**
     * Gets the base directory where configuration files are located.
     *
     * @return the base directory path
     */
    public Path getBasedir() {
        return basedir;
    }

    /**
     * Determines if an artifact is allowed from the specified repository.
     *
     * <p>Uses allow/deny rules with glob pattern matching on both groupId and artifactId.
     * Supports both groupId-only patterns (e.g., {@code org.graylog}) and full coordinate
     * patterns (e.g., {@code com.google:guava}, {@code com.company:test-*}).
     *
     * <p>By default, everything is denied unless explicitly allowed.
     * Deny rules override allow rules.
     *
     * <p><strong>Fail-secure behavior:</strong> If no configuration exists for a repository,
     * all artifacts are blocked. This ensures that missing configuration doesn't accidentally
     * allow unrestricted access.
     *
     * @param repositoryId the repository ID
     * @param artifact     the artifact to check
     * @return true if the artifact is allowed, false otherwise
     */
    public boolean isArtifactAllowed(String repositoryId, Artifact artifact) {
        final RepositoryRule rule = repositoryRules.get(repositoryId);
        // No configuration for this repository - deny by default (fail-secure)
        return rule != null && rule.isArtifactAllowed(artifact);
    }

    /**
     * Determines if metadata is allowed from the specified repository.
     *
     * <p>Uses allow/deny rules with glob pattern matching on groupId and optionally artifactId.
     * Supports both groupId-only patterns and full coordinate patterns.
     * Metadata without a groupId (repository-level metadata) is always allowed.
     *
     * <p><strong>Fail-secure behavior:</strong> If no configuration exists for a repository,
     * all metadata is blocked (except repository-level metadata which has no groupId).
     *
     * @param repositoryId the repository ID
     * @param metadata     the metadata to check
     * @return true if the metadata is allowed, false otherwise
     */
    public boolean isMetadataAllowed(String repositoryId, Metadata metadata) {
        final String groupId = metadata.getGroupId();
        if (groupId == null || groupId.isEmpty()) {
            // Allow metadata without groupId (e.g., repository-level metadata)
            return true;
        }

        final RepositoryRule rule = repositoryRules.get(repositoryId);
        // No configuration for this repository - deny by default (fail-secure)
        return rule != null && rule.isMetadataAllowed(metadata);
    }
}
