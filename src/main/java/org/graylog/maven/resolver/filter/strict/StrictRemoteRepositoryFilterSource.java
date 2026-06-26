package org.graylog.maven.resolver.filter.strict;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.spi.connector.filter.RemoteRepositoryFilter;
import org.eclipse.aether.spi.connector.filter.RemoteRepositoryFilterSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Strict Remote Repository Filter Source - SPI implementation.
 *
 * <p>This is the entry point for the Maven Resolver Remote Repository Filtering SPI.
 * Maven discovers this component via Sisu/JSR-330 dependency injection using the
 * {@code @Named("strict")} annotation.
 *
 * <p>The filter is <strong>enabled by default</strong> when the extension is registered in {@code .mvn/extensions.xml}.
 * It can be controlled via system properties:
 * <ul>
 *   <li>{@code aether.remoteRepositoryFilter.strict.enabled} - Enable/disable the filter globally (default: true)</li>
 *   <li>{@code aether.remoteRepositoryFilter.strict.basedir} - Base directory for configuration files
 *       (default: {@code .remoteRepositoryFilters} relative to local repository)</li>
 * </ul>
 *
 * <p>Configuration is loaded from {@code strict.properties} file in the basedir directory.
 *
 * <p><strong>Configuration lookup order:</strong><br>
 * When the {@code basedir} property is <em>not</em> set, configuration is looked up in this order
 * (first match wins):
 * <ol>
 *   <li>{@code <projectRoot>/.mvn/remoteRepositoryFilters/strict.properties} &mdash; the project-local
 *       config, where {@code <projectRoot>} is resolved from Maven's
 *       {@code maven.multiModuleProjectDirectory} property (the directory containing the topmost
 *       {@code .mvn}). A present file is authoritative even when empty (fail-secure).</li>
 *   <li>{@code <localRepository>/.remoteRepositoryFilters/strict.properties} &mdash; the global config
 *       in the local repository (e.g. {@code ~/.m2/repository}).</li>
 * </ol>
 * If the project root cannot be determined, only the local-repository location is used.
 *
 * <p>Setting {@code aether.remoteRepositoryFilter.strict.basedir} explicitly bypasses this lookup
 * and uses the given directory as the single configuration location.
 *
 * <p><strong>Usage Examples:</strong>
 * <pre>
 * # Explicitly disable the filter
 * mvn clean install -Daether.remoteRepositoryFilter.strict.enabled=false
 *
 * # Use custom basedir on command line
 * mvn clean install -Daether.remoteRepositoryFilter.strict.basedir=/path/to/config
 * </pre>
 */
@Named("strict")
@Singleton
public class StrictRemoteRepositoryFilterSource implements RemoteRepositoryFilterSource {

    private static final Logger logger = LoggerFactory.getLogger(StrictRemoteRepositoryFilterSource.class);

    private static final String CONFIG_PROP_ENABLED = "aether.remoteRepositoryFilter.strict.enabled";
    private static final String CONFIG_PROP_BASEDIR = "aether.remoteRepositoryFilter.strict.basedir";
    private static final String DEFAULT_BASEDIR = ".remoteRepositoryFilters";
    private static final String PROJECT_CONFIG_SUBDIR = ".mvn/remoteRepositoryFilters";
    private static final String MULTI_MODULE_PROJECT_DIRECTORY = "maven.multiModuleProjectDirectory";
    private static final String PLACEHOLDER_SESSION_ROOT = "${session.rootDirectory}";
    private static final String PLACEHOLDER_MULTI_MODULE = "${maven.multiModuleProjectDirectory}";

    /**
     * Constructor for Sisu/JSR-330 dependency injection.
     */
    @Inject
    public StrictRemoteRepositoryFilterSource() {
        logger.debug("StrictRemoteRepositoryFilterSource initialized");
    }

    @Override
    public RemoteRepositoryFilter getRemoteRepositoryFilter(RepositorySystemSession session) {
        if (!isEnabled(session)) {
            logger.debug("Strict filter is not enabled, abstaining from filtering");
            return null;
        }

        final StrictFilterConfiguration config = loadConfiguration(session);

        if (config.isEmpty()) {
            logger.warn("No configuration found for strict filter at: {} - BLOCKING ALL ARTIFACTS (fail-secure mode)",
                    config.getBasedir());
            logger.warn("Create strict.properties file with allow rules to permit artifacts");
        } else {
            logger.debug("Strict filter is active with configuration from: {}", config.getBasedir());
        }

        return new StrictRemoteRepositoryFilter(config);
    }

    /**
     * Checks if the filter is enabled via configuration properties.
     * The filter is enabled by default when the extension is registered.
     * Set the property to "false" to explicitly disable it.
     *
     * <p><strong>Fail-secure behavior:</strong> the filter is only disabled when the value is
     * explicitly {@code "false"} (case-insensitive). Any other value &mdash; including typos or
     * alternative truthy spellings such as {@code "yes"}, {@code "1"} or {@code "on"} &mdash;
     * leaves the filter <em>enabled</em>. This prevents a malformed property from silently
     * turning the security control off.
     *
     * @param session the repository system session
     * @return true if enabled (default), false only if explicitly set to "false"
     */
    private boolean isEnabled(RepositorySystemSession session) {
        final Object value = session.getConfigProperties().get(CONFIG_PROP_ENABLED);
        // Enabled by default; only an explicit "false" disables the filter (fail-secure).
        return value == null || !"false".equalsIgnoreCase(String.valueOf(value).trim());
    }

    /**
     * Loads the filter configuration.
     *
     * <p>When the {@code basedir} property is explicitly set, that single location is used
     * (backward-compatible behavior). Otherwise the project-local
     * {@code .mvn/remoteRepositoryFilters} directory is checked first, then the local
     * repository's {@code .remoteRepositoryFilters} directory; the first directory that
     * contains a {@code strict.properties} file wins.
     *
     * @param session the repository system session
     * @return the loaded configuration
     */
    private StrictFilterConfiguration loadConfiguration(RepositorySystemSession session) {
        final Path localRepoBasedir = session.getLocalRepository().getBasedir().toPath();
        final Path projectRoot = resolveProjectRoot(session);

        // A usable explicit basedir property wins outright and bypasses the project lookup.
        final String explicitBasedir = resolveExplicitBasedir(session, projectRoot);
        if (explicitBasedir != null) {
            return StrictFilterConfiguration.load(explicitBasedir, localRepoBasedir);
        }

        // No usable explicit basedir: prefer project-local config, then fall back to the local repository.
        final List<Path> candidates = new ArrayList<>();
        if (projectRoot != null) {
            candidates.add(projectRoot.resolve(PROJECT_CONFIG_SUBDIR));
        }
        candidates.add(localRepoBasedir.resolve(DEFAULT_BASEDIR));

        return StrictFilterConfiguration.loadFromCandidates(candidates);
    }

    /**
     * Returns the explicit basedir to use, with any unexpanded Maven root-directory placeholder
     * resolved, or {@code null} when no usable explicit basedir is configured.
     *
     * <p>Maven normally interpolates {@code ${session.rootDirectory}} (and the equivalent
     * {@code ${maven.multiModuleProjectDirectory}}) before the value reaches this extension.
     * However, in some resolver sessions &mdash; notably the early model/import-POM resolution
     * performed by IDEs such as IntelliJ during project import &mdash; {@code session.rootDirectory}
     * is not yet established, so the literal placeholder is passed through. Left unresolved it would
     * be treated as a relative path under the local repository, point at a non-existent directory,
     * and fail-secure block every artifact. This method resolves those placeholders from
     * {@code maven.multiModuleProjectDirectory} (the same project root, which <em>is</em> populated
     * in those sessions).
     *
     * <p>If the placeholder cannot be resolved (the project root is unknown, or an unrelated
     * placeholder remains), {@code null} is returned so the caller falls back to the project-local
     * and local-repository auto-discovery lookup rather than resolving a bogus directory.
     *
     * @param session     the repository system session
     * @param projectRoot the resolved project root, or {@code null} if it could not be determined
     * @return the usable (placeholder-resolved) basedir, or {@code null} to trigger auto-discovery
     */
    private String resolveExplicitBasedir(RepositorySystemSession session, Path projectRoot) {
        final String raw = getExplicitBasedir(session);
        if (raw == null || !raw.contains("${")) {
            return raw;
        }

        final String expanded = expandRootPlaceholders(raw, projectRoot);
        if (expanded.contains("${")) {
            logger.warn("strict filter: basedir '{}' contains an unresolved placeholder and the project "
                    + "root could not be determined; falling back to project-local/local-repository lookup", raw);
            return null;
        }
        logger.debug("strict filter: resolved unexpanded basedir placeholder '{}' to '{}'", raw, expanded);
        return expanded;
    }

    /**
     * Substitutes the {@code ${session.rootDirectory}} and {@code ${maven.multiModuleProjectDirectory}}
     * placeholders in the given basedir with the resolved project root. Both placeholders denote the
     * directory containing the topmost {@code .mvn}.
     *
     * @param basedir     the basedir value possibly containing root-directory placeholders
     * @param projectRoot the resolved project root, or {@code null} if it could not be determined
     * @return the basedir with known root placeholders substituted; unchanged if the root is unknown
     */
    private String expandRootPlaceholders(String basedir, Path projectRoot) {
        if (projectRoot == null) {
            return basedir;
        }
        final String root = projectRoot.toString();
        return basedir
                .replace(PLACEHOLDER_SESSION_ROOT, root)
                .replace(PLACEHOLDER_MULTI_MODULE, root);
    }

    /**
     * Returns the explicitly configured base directory, or {@code null} when the
     * {@code basedir} property is not set.
     *
     * <p>The value may arrive quoted from the command line, so surrounding double quotes
     * and then single quotes are unwrapped.
     *
     * @param session the repository system session
     * @return the unwrapped basedir property value, or {@code null} if unset
     */
    private String getExplicitBasedir(RepositorySystemSession session) {
        final Object value = session.getConfigProperties().get(CONFIG_PROP_BASEDIR);
        if (value == null) {
            return null;
        }
        // Unwrap double quotes, then single quotes. The value may come with quotes from command line.
        return StringUtils.unwrap(StringUtils.unwrap(String.valueOf(value), '"'), '\'');
    }

    /**
     * Determines the project root directory from the {@code maven.multiModuleProjectDirectory}
     * property, which Maven sets to the directory containing the topmost {@code .mvn}.
     *
     * <p>The session config properties are consulted first, falling back to the JVM system
     * property. Returns {@code null} when neither is available (e.g. when not run within a
     * Maven project), in which case the project-local lookup is skipped.
     *
     * @param session the repository system session
     * @return the project root path, or {@code null} if it cannot be determined
     */
    private Path resolveProjectRoot(RepositorySystemSession session) {
        final Object fromSession = session.getConfigProperties().get(MULTI_MODULE_PROJECT_DIRECTORY);
        // Maven launches the build with -Dmaven.multiModuleProjectDirectory=..., so the JVM
        // system property is the reliable runtime source; it is not always mirrored into the
        // resolver session, so the fallback below is required, not optional.
        final String value = fromSession != null
                ? String.valueOf(fromSession)
                : System.getProperty(MULTI_MODULE_PROJECT_DIRECTORY);

        if (value == null || value.isBlank()) {
            return null;
        }
        return Paths.get(value);
    }
}
