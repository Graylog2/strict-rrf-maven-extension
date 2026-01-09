package org.graylog.maven.resolver.filter.strict;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.metadata.Metadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

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
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

    private final Path basedir;
    private final Map<String, RepositoryRule> repositoryRules;

    private StrictFilterConfiguration(Path basedir, Map<String, RepositoryRule> repositoryRules) {
        this.basedir = basedir;
        this.repositoryRules = Map.copyOf(repositoryRules);
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

        return new StrictFilterConfiguration(basedir, loadPropertiesFile(configFile));
    }

    private static Map<String, RepositoryRule> loadPropertiesFile(Path file) {
        logger.debug("Loading configuration from: {}", file);

        if (!Files.exists(file)) {
            logger.debug("Configuration file does not exist: {}", file);
            return Map.of();
        }

        // Avoid loading excessively large files
        if (!validateFileSize(file)) {
            return Map.of();
        }

        final Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(file)) {
            properties.load(input);
        } catch (IOException e) {
            logger.warn("Failed to read configuration file: {}", file, e);
            return Map.of();
        }

        // First collect all repository IDs
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

            final Set<String> patterns = Arrays.stream(value.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toSet());

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
        final Map<String, RepositoryRule> repositoryRules = new HashMap<>();
        final Set<String> allRepoIds = new HashSet<>();
        allRepoIds.addAll(allowPatterns.keySet());
        allRepoIds.addAll(denyPatterns.keySet());

        for (final String repoId : allRepoIds) {
            final Set<String> allow = allowPatterns.getOrDefault(repoId, Set.of());
            final Set<String> deny = denyPatterns.getOrDefault(repoId, Set.of());

            repositoryRules.put(repoId, new RepositoryRule(allow, deny));
            logger.info("Created rule for repository '{}': {} allow patterns, {} deny patterns",
                    repoId, allow.size(), deny.size());
        }

        if (repositoryRules.isEmpty()) {
            logger.debug("No repository rules loaded from: {}", file);
        }

        return repositoryRules;
    }

    /**
     * Validates that a file size is within acceptable limits.
     *
     * @param file the file to validate
     * @return true if the file size is acceptable, false otherwise
     */
    private static boolean validateFileSize(Path file) {
        try {
            final long fileSize = Files.size(file);
            if (fileSize > MAX_FILE_SIZE) {
                logger.warn("Configuration file {} is too large ({} bytes, max {} bytes), skipping",
                        file, fileSize, MAX_FILE_SIZE);
                return false;
            }
            return true;
        } catch (IOException e) {
            logger.warn("Failed to check size of configuration file: {}", file, e);
            return false;
        }
    }

    private static Path resolveBasedir(String basedirConfig, Path localRepoBasedir) {
        final Path path = Paths.get(basedirConfig);
        if (path.isAbsolute()) {
            return path;
        }
        // Relative path: resolve from local repository base directory
        return localRepoBasedir.resolve(basedirConfig);
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
