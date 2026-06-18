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
 * <p><strong>Project-Specific Configuration (Maven 3.9+):</strong><br>
 * To use project-specific configuration, add to {@code .mvn/maven.config}:
 * <pre>
 * -Daether.remoteRepositoryFilter.strict.basedir=${session.rootDirectory}/.mvn/remoteRepositoryFilters
 * </pre>
 * Then create {@code .mvn/remoteRepositoryFilters/strict.properties} in your project root.
 *
 * <p><strong>Note:</strong> In Maven 3.9.x, {@code ${session.rootDirectory}} is only available for
 * interpolation in {@code .mvn/maven.config} and command-line arguments, not as a runtime property.
 * Maven 4.0+ will support it as a full property.
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
            logger.info("Strict filter is active with configuration from: {}", config.getBasedir());
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
     * Loads the filter configuration from the configured base directory.
     *
     * @param session the repository system session
     * @return the loaded configuration
     */
    private StrictFilterConfiguration loadConfiguration(RepositorySystemSession session) {
        final String basedir = getBasedir(session);
        final Path localRepoBasedir = session.getLocalRepository().getBasedir().toPath();
        return StrictFilterConfiguration.load(basedir, localRepoBasedir);
    }

    /**
     * Gets the base directory for configuration files from session properties.
     *
     * @param session the repository system session
     * @return the base directory path (relative or absolute)
     */
    private String getBasedir(RepositorySystemSession session) {
        final Object value = session.getConfigProperties().get(CONFIG_PROP_BASEDIR);
        if (value != null) {
            // Unwrap double quotes, then single quotes. The value may come with quotes from command line.
            return StringUtils.unwrap(StringUtils.unwrap(String.valueOf(value), '"'), '\'');
        }
        // Default: .remoteRepositoryFilters (relative to local repository)
        return DEFAULT_BASEDIR;
    }
}
