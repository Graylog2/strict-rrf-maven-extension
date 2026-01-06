package org.graylog.maven.resolver.filter.strict;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.spi.connector.filter.RemoteRepositoryFilter;
import org.eclipse.aether.spi.connector.filter.RemoteRepositoryFilterSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 *   <li>{@code aether.remoteRepositoryFilter.strict.enabled} - Enable/disable the filter (default: true)</li>
 *   <li>{@code aether.remoteRepositoryFilter.strict.basedir} - Base directory for configuration files
 *       (default: {@code ${localRepo}/.remoteRepositoryFilters/strict})</li>
 *   <li>{@code aether.remoteRepositoryFilter.strict.{repositoryId}} - Enable/disable for specific repository</li>
 * </ul>
 *
 * <p>Configuration is loaded from {@code strict.properties} file in the basedir directory.
 *
 * <p><strong>Usage Examples:</strong>
 * <pre>
 * # Enabled by default when extension is registered
 * # Configuration in ~/.m2/repository/.remoteRepositoryFilters/strict/strict.properties
 *
 * # Explicitly disable the filter
 * mvn clean install -Daether.remoteRepositoryFilter.strict.enabled=false
 * </pre>
 */
@Named("strict")
@Singleton
public class StrictRemoteRepositoryFilterSource implements RemoteRepositoryFilterSource {

    private static final Logger logger = LoggerFactory.getLogger(StrictRemoteRepositoryFilterSource.class);

    private static final String CONFIG_PROP_ENABLED = "aether.remoteRepositoryFilter.strict.enabled";
    private static final String CONFIG_PROP_BASEDIR = "aether.remoteRepositoryFilter.strict.basedir";
    private static final String DEFAULT_BASEDIR = ".remoteRepositoryFilters/strict";

    /**
     * Constructor for Sisu/JSR-330 dependency injection.
     */
    @Inject
    public StrictRemoteRepositoryFilterSource() {
        logger.debug("StrictRemoteRepositoryFilterSource initialized");
    }

    @Override
    public RemoteRepositoryFilter getRemoteRepositoryFilter(RepositorySystemSession session) {
        // Check if filter is enabled
        if (!isEnabled(session)) {
            logger.debug("Strict filter is not enabled, abstaining from filtering");
            return null; // Abstain from filtering
        }

        // Load configuration
        StrictFilterConfiguration config = loadConfiguration(session);

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
     * @param session the repository system session
     * @return true if enabled (default), false if explicitly disabled
     */
    private boolean isEnabled(RepositorySystemSession session) {
        Object value = session.getConfigProperties().get(CONFIG_PROP_ENABLED);
        if (value == null) {
            return true; // Enabled by default
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    /**
     * Loads the filter configuration from the configured base directory.
     *
     * @param session the repository system session
     * @return the loaded configuration
     */
    private StrictFilterConfiguration loadConfiguration(RepositorySystemSession session) {
        String basedir = getBasedir(session);
        return StrictFilterConfiguration.load(basedir, session.getLocalRepository().getBasedir().toPath());
    }

    /**
     * Gets the base directory for configuration files from session properties.
     *
     * @param session the repository system session
     * @return the base directory path (relative or absolute)
     */
    private String getBasedir(RepositorySystemSession session) {
        Object value = session.getConfigProperties().get(CONFIG_PROP_BASEDIR);
        if (value != null) {
            return String.valueOf(value);
        }
        // Default: ${localRepo}/.remoteRepositoryFilters/strict
        return DEFAULT_BASEDIR;
    }
}
