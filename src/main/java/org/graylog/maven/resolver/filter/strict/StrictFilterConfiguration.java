package org.graylog.maven.resolver.filter.strict;

import org.apache.commons.lang3.StringUtils;
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
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;
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
 * <li>GroupId-only (exact): {@code org.graylog} matches only {@code org.graylog:*}, not
 *     sub-groups such as {@code org.graylog.plugin:*}</li>
 * <li>Dot-anchored groupId: {@code org.graylog.*} matches only sub-groups such as
 *     {@code org.graylog.plugin:*}; combine as {@code org.graylog,org.graylog.*} to match both</li>
 * <li>Wildcard groupId: {@code com.google*} matches {@code com.google:*}, {@code com.googlecode:*}</li>
 * <li>Coordinate with wildcard: {@code com.opensaml:*} matches all artifacts in {@code com.opensaml}</li>
 * <li>Coordinate with pattern: {@code com.company:test-*} matches {@code com.company:test-utils}, etc.</li>
 * <li>Exact coordinate: {@code com.google:guava} matches only {@code com.google:guava}</li>
 * </ul>
 *
 * <p>By default, everything is denied unless explicitly allowed.
 * Deny rules override allow rules. Supports glob patterns with * wildcard.
 *
 * <p><strong>Caution &mdash; wildcards are greedy across dots:</strong> {@code .} is not treated as
 * a separator, so {@code *} matches arbitrarily deep. A bare-prefix pattern such as
 * {@code org.graylog*} also matches adjacent names like {@code org.graylognasty} (a potential
 * typosquat), and {@code com.*} allows the entire {@code com} namespace. Prefer exact patterns
 * ({@code org.graylog}) or dot-anchored prefixes ({@code org.graylog.*}) in allow rules.
 *
 * <p><strong>Fail-secure validation:</strong> a repository is blocked entirely (as if it had no
 * configuration) when any of its rules is ambiguous or invalid, so that a rule can never be
 * silently ignored:
 * <ul>
 * <li>the same key appears more than once (plain {@link Properties} would keep only the last line)</li>
 * <li>a pattern contains characters other than letters, digits, {@code .}, {@code _}, {@code -} and
 *     {@code *}, or more than one {@code :} (e.g. a version such as {@code g:a:1.0}, {@code ?},
 *     whitespace, or {@code %regex[...]})</li>
 * <li>a pattern has an empty groupId or artifactId part (e.g. {@code g:} or {@code :a})</li>
 * </ul>
 */
public class StrictFilterConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(StrictFilterConfiguration.class);
    private static final String CONFIG_FILE_NAME = "strict.properties";
    private static final String REPO_PREFIX = "repo.";
    private static final String ALLOW_SUFFIX = ".allow";
    private static final String DENY_SUFFIX = ".deny";
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;
    // Maven's coordinate characters plus '*', and at most one ':' between groupId and artifactId
    private static final Pattern VALID_RULE_PATTERN =
            Pattern.compile("[A-Za-z0-9_.*-]+(?::[A-Za-z0-9_.*-]+)?");

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

    /**
     * Loads configuration from the first candidate directory that contains a
     * {@code strict.properties} file.
     *
     * <p>Candidates are checked in order. The first directory whose
     * {@code strict.properties} file exists is selected and loaded &mdash; a present
     * file is authoritative even when empty (fail-secure). If no candidate contains a
     * config file, the <strong>last</strong> candidate is used as the reported base
     * directory so that the resulting (empty) configuration points at a sensible
     * fallback location.
     *
     * @param candidateDirs the candidate directories, in priority order (must not be empty)
     * @return a configuration instance loaded from the selected directory
     * @throws IllegalArgumentException if {@code candidateDirs} is empty
     */
    public static StrictFilterConfiguration loadFromCandidates(List<Path> candidateDirs) {
        if (candidateDirs.isEmpty()) {
            throw new IllegalArgumentException("candidateDirs must not be empty");
        }

        Path selected = candidateDirs.get(candidateDirs.size() - 1);
        for (final Path dir : candidateDirs) {
            if (Files.exists(dir.resolve(CONFIG_FILE_NAME))) {
                selected = dir;
                break;
            }
        }

        return new StrictFilterConfiguration(selected, loadPropertiesFile(selected.resolve(CONFIG_FILE_NAME)));
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

        final DuplicateTrackingProperties properties = new DuplicateTrackingProperties();
        try (InputStream input = Files.newInputStream(file)) {
            properties.load(input);
        } catch (IOException e) {
            logger.warn("Failed to read configuration file: {}", file, e);
            return Map.of();
        }

        final Map<String, Set<String>> allowPatterns = new HashMap<>();
        final Map<String, Set<String>> denyPatterns = new HashMap<>();
        // Repositories with an ambiguous or invalid rule get no rule at all, which blocks them (fail-secure)
        final Set<String> blockedRepoIds = new HashSet<>();

        for (final String key : properties.stringPropertyNames()) {
            final RuleKey ruleKey = parseRuleKey(key, file);
            if (ruleKey == null) {
                continue;
            }

            if (properties.isDuplicate(key)) {
                logger.warn("Duplicate property '{}' in {}: only one line per key is allowed. Blocking all"
                        + " artifacts from repository '{}' until the duplicate is removed.", key, file, ruleKey.repoId());
                blockedRepoIds.add(ruleKey.repoId());
                continue;
            }

            final String value = properties.getProperty(key);
            if (StringUtils.isBlank(value)) {
                logger.warn("Empty value for property '{}' in {}: skipping. A blank '.deny' rule blocks"
                        + " nothing; a blank '.allow' rule denies everything for that repository.", key, file);
                continue;
            }

            final Set<String> patterns = Arrays.stream(value.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toSet());

            final List<String> invalidPatterns = patterns.stream()
                    .filter(p -> !VALID_RULE_PATTERN.matcher(p).matches())
                    .sorted()
                    .toList();
            if (!invalidPatterns.isEmpty()) {
                logger.warn("Invalid pattern(s) {} for property '{}' in {}: patterns may only contain letters, digits,"
                                + " '.', '_', '-' and '*', with at most one ':' between groupId and artifactId."
                                + " Blocking all artifacts from repository '{}' until fixed.",
                        invalidPatterns, key, file, ruleKey.repoId());
                blockedRepoIds.add(ruleKey.repoId());
                continue;
            }

            if (!patterns.isEmpty()) {
                final Map<String, Set<String>> target = ruleKey.allow() ? allowPatterns : denyPatterns;
                target.computeIfAbsent(ruleKey.repoId(), k -> new HashSet<>()).addAll(patterns);
                logger.debug("Loaded {} {} patterns for repository '{}': {}",
                        patterns.size(), ruleKey.allow() ? "allow" : "deny", ruleKey.repoId(), patterns);
            }
        }

        return buildRules(allowPatterns, denyPatterns, blockedRepoIds, file);
    }

    /**
     * Parses a property key of the form {@code repo.<repositoryId>.allow} or {@code repo.<repositoryId>.deny}.
     *
     * @param key  the property key
     * @param file the configuration file, for log messages
     * @return the parsed key, or {@code null} if the key is invalid (a warning is logged)
     */
    private static RuleKey parseRuleKey(String key, Path file) {
        if (!key.startsWith(REPO_PREFIX)) {
            logger.warn("Skipping invalid property '{}' in {}: key must start with 'repo.'", key, file);
            return null;
        }

        final String remainder = key.substring(REPO_PREFIX.length());
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
            return null;
        }

        if (repoId.isEmpty()) {
            logger.warn("Skipping invalid property '{}' in {}: empty repository ID", key, file);
            return null;
        }

        return new RuleKey(repoId, isAllow);
    }

    private static Map<String, RepositoryRule> buildRules(Map<String, Set<String>> allowPatterns,
                                                          Map<String, Set<String>> denyPatterns,
                                                          Set<String> blockedRepoIds,
                                                          Path file) {
        final Map<String, RepositoryRule> repositoryRules = new HashMap<>();
        final Set<String> allRepoIds = new HashSet<>();
        allRepoIds.addAll(allowPatterns.keySet());
        allRepoIds.addAll(denyPatterns.keySet());
        allRepoIds.removeAll(blockedRepoIds);

        for (final String repoId : allRepoIds) {
            final Set<String> allow = allowPatterns.getOrDefault(repoId, Set.of());
            final Set<String> deny = denyPatterns.getOrDefault(repoId, Set.of());

            repositoryRules.put(repoId, new RepositoryRule(allow, deny));
            logger.debug("Created rule for repository '{}': {} allow patterns, {} deny patterns",
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
     * Metadata without a groupId (repository-level metadata) is allowed only for repositories
     * that have an explicit configuration.
     *
     * <p><strong>Fail-secure behavior:</strong> If no configuration exists for a repository,
     * all metadata is blocked &mdash; including repository-level metadata that has no groupId.
     * Repository-level metadata carries no artifact coordinate to match against the rules, so
     * it is only permitted for repositories the user has explicitly configured.
     *
     * @param repositoryId the repository ID
     * @param metadata     the metadata to check
     * @return true if the metadata is allowed, false otherwise
     */
    public boolean isMetadataAllowed(String repositoryId, Metadata metadata) {
        final RepositoryRule rule = repositoryRules.get(repositoryId);
        // No configuration for this repository - deny by default (fail-secure)
        if (rule == null) {
            return false;
        }

        // Repository-level metadata (no groupId) has no coordinate to match; allow it only
        // because the repository is explicitly configured (checked above).
        return StringUtils.isEmpty(metadata.getGroupId()) || rule.isMetadataAllowed(metadata);
    }

    /**
     * A parsed {@code repo.<repositoryId>.allow|deny} property key.
     */
    private record RuleKey(String repoId, boolean allow) {
    }

    /**
     * {@link Properties} that remembers keys defined more than once. Plain {@code Properties}
     * silently keeps only the last value, which would drop an earlier rule unnoticed.
     */
    private static final class DuplicateTrackingProperties extends Properties {
        private static final long serialVersionUID = 1L;

        private final Set<String> duplicateKeys = new HashSet<>();

        @Override
        public Object put(Object key, Object value) {
            // Properties.load() stores every parsed line through put()
            if (containsKey(key)) {
                duplicateKeys.add(String.valueOf(key));
            }
            return super.put(key, value);
        }

        boolean isDuplicate(String key) {
            return duplicateKeys.contains(key);
        }
    }
}
