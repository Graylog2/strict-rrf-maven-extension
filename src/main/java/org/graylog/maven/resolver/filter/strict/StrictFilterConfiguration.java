package org.graylog.maven.resolver.filter.strict;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.metadata.Metadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Configuration for the strict remote repository filter.
 * Loads per-repository filtering rules from text files in the format: strict-{repositoryId}.txt
 *
 * <p>Configuration files contain one groupId per line. Lines starting with '#' are treated as comments.
 * Empty lines are ignored.
 */
public class StrictFilterConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(StrictFilterConfiguration.class);

    private final Path basedir;
    private final Map<String, Set<String>> allowedGroupIds; // repositoryId -> Set<groupId>

    private StrictFilterConfiguration(Path basedir, Map<String, Set<String>> allowedGroupIds) {
        this.basedir = basedir;
        this.allowedGroupIds = allowedGroupIds;
    }

    /**
     * Loads configuration from the specified base directory.
     *
     * @param basedirConfig the base directory configuration (can be relative or absolute)
     * @param localRepoBasedir the local repository base directory for resolving relative paths
     * @return a configuration instance
     */
    public static StrictFilterConfiguration load(String basedirConfig, Path localRepoBasedir) {
        Path basedir = resolveBasedir(basedirConfig, localRepoBasedir);

        if (!Files.exists(basedir) || !Files.isDirectory(basedir)) {
            logger.debug("Configuration directory does not exist: {}", basedir);
            return empty(basedir);
        }

        Map<String, Set<String>> allowedGroupIds = new HashMap<>();

        try {
            // Load configuration files: strict-{repositoryId}.txt
            Files.list(basedir)
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().startsWith("strict-"))
                    .filter(p -> p.getFileName().toString().endsWith(".txt"))
                    .forEach(file -> loadRepositoryConfig(file, allowedGroupIds));
        } catch (IOException e) {
            logger.warn("Failed to read configuration from: {}", basedir, e);
        }

        return new StrictFilterConfiguration(basedir, allowedGroupIds);
    }

    private static void loadRepositoryConfig(Path file, Map<String, Set<String>> allowedGroupIds) {
        String fileName = file.getFileName().toString();
        // Extract repository ID: strict-{repoId}.txt
        String repoId = fileName.substring("strict-".length(), fileName.length() - ".txt".length());

        logger.debug("Loading configuration for repository: {} from file: {}", repoId, file);

        Set<String> groupIds = new HashSet<>();
        try (BufferedReader reader = Files.newBufferedReader(file)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                // Skip empty lines and comments
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                groupIds.add(line);
            }
        } catch (IOException e) {
            logger.warn("Failed to read configuration file: {}", file, e);
        }

        if (!groupIds.isEmpty()) {
            allowedGroupIds.put(repoId, groupIds);
            logger.info("Loaded {} allowed groupIds for repository: {}", groupIds.size(), repoId);
        }
    }

    private static Path resolveBasedir(String basedirConfig, Path localRepoBasedir) {
        Path path = Paths.get(basedirConfig);
        if (path.isAbsolute()) {
            return path;
        }
        // Relative path: resolve from local repository
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
        return allowedGroupIds.isEmpty();
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
     * <p><strong>Customization Point:</strong> Modify this method to implement your filtering logic.
     * The default implementation checks if the artifact's groupId matches any allowed groupId
     * using prefix matching (e.g., "org.graylog" matches "org.graylog.plugin").
     *
     * @param repositoryId the repository ID
     * @param artifact the artifact to check
     * @return true if the artifact is allowed, false otherwise
     */
    public boolean isArtifactAllowed(String repositoryId, Artifact artifact) {
        Set<String> allowed = allowedGroupIds.get(repositoryId);
        if (allowed == null) {
            // No configuration for this repository - accept by default
            return true;
        }

        String groupId = artifact.getGroupId();
        // Check exact match or prefix match (e.g., "org.graylog" matches "org.graylog.*")
        for (String allowedGroupId : allowed) {
            if (groupId.equals(allowedGroupId) || groupId.startsWith(allowedGroupId + ".")) {
                return true;
            }
        }

        return false;
    }

    /**
     * Determines if metadata is allowed from the specified repository.
     *
     * <p><strong>Customization Point:</strong> Modify this method to implement your filtering logic.
     * The default implementation uses the same logic as artifact filtering but accepts metadata
     * without a groupId (e.g., repository-level metadata).
     *
     * @param repositoryId the repository ID
     * @param metadata the metadata to check
     * @return true if the metadata is allowed, false otherwise
     */
    public boolean isMetadataAllowed(String repositoryId, Metadata metadata) {
        Set<String> allowed = allowedGroupIds.get(repositoryId);
        if (allowed == null) {
            return true;
        }

        String groupId = metadata.getGroupId();
        if (groupId == null || groupId.isEmpty()) {
            // Allow metadata without groupId (e.g., repository-level metadata)
            return true;
        }

        for (String allowedGroupId : allowed) {
            if (groupId.equals(allowedGroupId) || groupId.startsWith(allowedGroupId + ".")) {
                return true;
            }
        }

        return false;
    }
}
