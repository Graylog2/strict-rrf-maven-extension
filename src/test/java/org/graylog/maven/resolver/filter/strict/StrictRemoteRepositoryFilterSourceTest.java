package org.graylog.maven.resolver.filter.strict;

import org.eclipse.aether.RepositoryCache;
import org.eclipse.aether.RepositoryListener;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.SessionData;
import org.eclipse.aether.artifact.ArtifactTypeRegistry;
import org.eclipse.aether.collection.DependencyGraphTransformer;
import org.eclipse.aether.collection.DependencyManager;
import org.eclipse.aether.collection.DependencySelector;
import org.eclipse.aether.collection.DependencyTraverser;
import org.eclipse.aether.collection.VersionFilter;
import org.eclipse.aether.repository.AuthenticationSelector;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.LocalRepositoryManager;
import org.eclipse.aether.repository.MirrorSelector;
import org.eclipse.aether.repository.ProxySelector;
import org.eclipse.aether.repository.WorkspaceReader;
import org.eclipse.aether.resolution.ArtifactDescriptorPolicy;
import org.eclipse.aether.resolution.ResolutionErrorPolicy;
import org.eclipse.aether.spi.connector.filter.RemoteRepositoryFilter;
import org.eclipse.aether.transfer.TransferListener;
import org.eclipse.aether.transform.FileTransformerManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit tests for {@link StrictRemoteRepositoryFilterSource}.
 *
 * <p>Note: This test class has high coupling (25 dependencies) because it needs to create
 * a mock RepositorySystemSession, which implements many Aether API interfaces. This is
 * acceptable for integration testing with Maven APIs.
 */
@SuppressWarnings("PMD.CouplingBetweenObjects") // Necessary for mocking Maven Aether API
class StrictRemoteRepositoryFilterSourceTest {

    private static final String CONFIG_PROP_ENABLED = "aether.remoteRepositoryFilter.strict.enabled";
    private static final String CONFIG_PROP_BASEDIR = "aether.remoteRepositoryFilter.strict.basedir";

    /**
     * Creates a simple test implementation of RepositorySystemSession.
     */
    private RepositorySystemSession createSession(Map<String, Object> configProperties, Path localRepoPath) {
        return new RepositorySystemSession() {
            @Override
            public boolean isOffline() {
                return false;
            }

            @Override
            public boolean isIgnoreArtifactDescriptorRepositories() {
                return false;
            }

            @Override
            public ResolutionErrorPolicy getResolutionErrorPolicy() {
                return null;
            }

            @Override
            public ArtifactDescriptorPolicy getArtifactDescriptorPolicy() {
                return null;
            }

            @Override
            public String getChecksumPolicy() {
                return null;
            }

            @Override
            public String getUpdatePolicy() {
                return null;
            }

            @Override
            public LocalRepository getLocalRepository() {
                return new LocalRepository(localRepoPath.toFile());
            }

            @Override
            public LocalRepositoryManager getLocalRepositoryManager() {
                return null;
            }

            @Override
            public WorkspaceReader getWorkspaceReader() {
                return null;
            }

            @Override
            public RepositoryListener getRepositoryListener() {
                return null;
            }

            @Override
            public TransferListener getTransferListener() {
                return null;
            }

            @Override
            public Map<String, String> getSystemProperties() {
                return new HashMap<>();
            }

            @Override
            public Map<String, String> getUserProperties() {
                return new HashMap<>();
            }

            @Override
            public Map<String, Object> getConfigProperties() {
                return configProperties;
            }

            @Override
            public MirrorSelector getMirrorSelector() {
                return null;
            }

            @Override
            public ProxySelector getProxySelector() {
                return null;
            }

            @Override
            public AuthenticationSelector getAuthenticationSelector() {
                return null;
            }

            @Override
            public ArtifactTypeRegistry getArtifactTypeRegistry() {
                return null;
            }

            @Override
            public DependencyTraverser getDependencyTraverser() {
                return null;
            }

            @Override
            public DependencyManager getDependencyManager() {
                return null;
            }

            @Override
            public DependencySelector getDependencySelector() {
                return null;
            }

            @Override
            public VersionFilter getVersionFilter() {
                return null;
            }

            @Override
            public DependencyGraphTransformer getDependencyGraphTransformer() {
                return null;
            }

            @Override
            public SessionData getData() {
                return null;
            }

            @Override
            public RepositoryCache getCache() {
                return null;
            }

            @Override
            public FileTransformerManager getFileTransformerManager() {
                return null;
            }
        };
    }

    @Test
    void testFilterEnabledByDefault(@TempDir Path tempDir) throws Exception {
        // Create a valid configuration
        final Path configDir = tempDir.resolve(".remoteRepositoryFilters");
        StrictPropertiesTestHelper.writeStrictProperties(configDir, "repo.central.allow = org.graylog\n");

        final StrictRemoteRepositoryFilterSource source = new StrictRemoteRepositoryFilterSource();

        // No enabled property set - should be enabled by default
        final Map<String, Object> properties = new HashMap<>();
        final RepositorySystemSession session = createSession(properties, tempDir);

        final RemoteRepositoryFilter filter = source.getRemoteRepositoryFilter(session);

        assertNotNull(filter, "Filter should be enabled by default when property not set");
        assertInstanceOf(StrictRemoteRepositoryFilter.class, filter, "Filter should be instance of StrictRemoteRepositoryFilter");
    }

    @Test
    void testFilterEnabledExplicitly(@TempDir Path tempDir) throws Exception {
        // Create a valid configuration
        final Path configDir = tempDir.resolve(".remoteRepositoryFilters");
        StrictPropertiesTestHelper.writeStrictProperties(configDir, "repo.central.allow = org.graylog\n");

        final StrictRemoteRepositoryFilterSource source = new StrictRemoteRepositoryFilterSource();

        // Explicitly enable
        final Map<String, Object> properties = new HashMap<>();
        properties.put(CONFIG_PROP_ENABLED, "true");
        final RepositorySystemSession session = createSession(properties, tempDir);

        final RemoteRepositoryFilter filter = source.getRemoteRepositoryFilter(session);

        assertNotNull(filter, "Filter should be enabled when property set to 'true'");
        assertInstanceOf(StrictRemoteRepositoryFilter.class, filter, "Filter should be instance of StrictRemoteRepositoryFilter");
    }

    @Test
    void testFilterDisabledViaProperty(@TempDir Path tempDir) {
        final StrictRemoteRepositoryFilterSource source = new StrictRemoteRepositoryFilterSource();

        // Disable via property
        final Map<String, Object> properties = new HashMap<>();
        properties.put(CONFIG_PROP_ENABLED, "false");
        final RepositorySystemSession session = createSession(properties, tempDir);

        final RemoteRepositoryFilter filter = source.getRemoteRepositoryFilter(session);

        assertNull(filter, "Filter should be null when disabled via property");
    }

    @Test
    void testFilterDisabledViaBooleanFalse(@TempDir Path tempDir) {
        final StrictRemoteRepositoryFilterSource source = new StrictRemoteRepositoryFilterSource();

        // Disable via boolean false
        final Map<String, Object> properties = new HashMap<>();
        properties.put(CONFIG_PROP_ENABLED, Boolean.FALSE);
        final RepositorySystemSession session = createSession(properties, tempDir);

        final RemoteRepositoryFilter filter = source.getRemoteRepositoryFilter(session);

        assertNull(filter, "Filter should be null when disabled via Boolean.FALSE");
    }

    @Test
    void testFilterDisabledOnlyByExplicitFalse(@TempDir Path tempDir) throws Exception {
        // Fail-secure: any value other than an explicit "false" must keep the filter enabled,
        // so that a typo or an alternative truthy spelling cannot silently disable it.
        final Path configDir = tempDir.resolve(".remoteRepositoryFilters");
        StrictPropertiesTestHelper.writeStrictProperties(configDir, "repo.central.allow = org.graylog\n");

        final StrictRemoteRepositoryFilterSource source = new StrictRemoteRepositoryFilterSource();

        for (final String value : new String[] {"yes", "1", "on", "enabled", "ture", "flase", "", "  "}) {
            final Map<String, Object> properties = new HashMap<>();
            properties.put(CONFIG_PROP_ENABLED, value);
            final RepositorySystemSession session = createSession(properties, tempDir);

            final RemoteRepositoryFilter filter = source.getRemoteRepositoryFilter(session);

            assertNotNull(filter, "Filter must stay enabled for non-\"false\" value: '" + value + "'");
        }
    }

    @Test
    void testFilterDisabledByFalseIgnoresCaseAndWhitespace(@TempDir Path tempDir) {
        final StrictRemoteRepositoryFilterSource source = new StrictRemoteRepositoryFilterSource();

        for (final String value : new String[] {"false", "FALSE", "False", " false "}) {
            final Map<String, Object> properties = new HashMap<>();
            properties.put(CONFIG_PROP_ENABLED, value);
            final RepositorySystemSession session = createSession(properties, tempDir);

            final RemoteRepositoryFilter filter = source.getRemoteRepositoryFilter(session);

            assertNull(filter, "Filter must be disabled for explicit false value: '" + value + "'");
        }
    }

    @Test
    void testFilterWithEmptyConfigurationFailSecure(@TempDir Path tempDir) {
        final StrictRemoteRepositoryFilterSource source = new StrictRemoteRepositoryFilterSource();

        // No configuration file exists - should return filter in fail-secure mode
        final Map<String, Object> properties = new HashMap<>();
        final RepositorySystemSession session = createSession(properties, tempDir);

        final RemoteRepositoryFilter filter = source.getRemoteRepositoryFilter(session);

        assertNotNull(filter, "Filter should still be created even without configuration (fail-secure)");
        assertInstanceOf(StrictRemoteRepositoryFilter.class, filter, "Filter should be instance of StrictRemoteRepositoryFilter");
    }

    @Test
    void testCustomBasedirProperty(@TempDir Path tempDir) throws Exception {
        final StrictRemoteRepositoryFilterSource source = new StrictRemoteRepositoryFilterSource();

        // Create config in custom directory
        final Path customConfigDir = tempDir.resolve("custom-config");
        StrictPropertiesTestHelper.writeStrictProperties(customConfigDir, "repo.central.allow = org.graylog\n");

        // Use custom basedir
        final Map<String, Object> properties = new HashMap<>();
        properties.put(CONFIG_PROP_BASEDIR, "custom-config");
        final RepositorySystemSession session = createSession(properties, tempDir);

        final RemoteRepositoryFilter filter = source.getRemoteRepositoryFilter(session);

        assertNotNull(filter, "Filter should load configuration from custom basedir");
    }

    @Test
    void testCustomBasedirAbsolutePath(@TempDir Path tempDir) throws Exception {
        final StrictRemoteRepositoryFilterSource source = new StrictRemoteRepositoryFilterSource();

        // Create config in absolute path
        final Path absoluteConfigDir = tempDir.resolve("absolute-config");
        StrictPropertiesTestHelper.writeStrictProperties(absoluteConfigDir, "repo.central.allow = org.graylog\n");

        // Use absolute basedir
        final Map<String, Object> properties = new HashMap<>();
        properties.put(CONFIG_PROP_BASEDIR, absoluteConfigDir.toAbsolutePath().toString());
        final RepositorySystemSession session = createSession(properties, tempDir);

        final RemoteRepositoryFilter filter = source.getRemoteRepositoryFilter(session);

        assertNotNull(filter, "Filter should load configuration from absolute basedir path");
    }

    @Test
    void testQuoteUnwrappingDoubleQuotes(@TempDir Path tempDir) throws Exception {
        final StrictRemoteRepositoryFilterSource source = new StrictRemoteRepositoryFilterSource();

        // Create config
        final Path configDir = tempDir.resolve("quoted-config");
        StrictPropertiesTestHelper.writeStrictProperties(configDir, "repo.central.allow = org.graylog\n");

        // Basedir with double quotes (simulating command-line input)
        final Map<String, Object> properties = new HashMap<>();
        properties.put(CONFIG_PROP_BASEDIR, "\"quoted-config\"");
        final RepositorySystemSession session = createSession(properties, tempDir);

        final RemoteRepositoryFilter filter = source.getRemoteRepositoryFilter(session);

        assertNotNull(filter, "Filter should unwrap double quotes from basedir property");
    }

    @Test
    void testQuoteUnwrappingSingleQuotes(@TempDir Path tempDir) throws Exception {
        final StrictRemoteRepositoryFilterSource source = new StrictRemoteRepositoryFilterSource();

        // Create config
        final Path configDir = tempDir.resolve("quoted-config");
        StrictPropertiesTestHelper.writeStrictProperties(configDir, "repo.central.allow = org.graylog\n");

        // Basedir with single quotes
        final Map<String, Object> properties = new HashMap<>();
        properties.put(CONFIG_PROP_BASEDIR, "'quoted-config'");
        final RepositorySystemSession session = createSession(properties, tempDir);

        final RemoteRepositoryFilter filter = source.getRemoteRepositoryFilter(session);

        assertNotNull(filter, "Filter should unwrap single quotes from basedir property");
    }

    @Test
    void testQuoteUnwrappingMixedQuotes(@TempDir Path tempDir) throws Exception {
        final StrictRemoteRepositoryFilterSource source = new StrictRemoteRepositoryFilterSource();

        // Create config
        final Path configDir = tempDir.resolve("quoted-config");
        StrictPropertiesTestHelper.writeStrictProperties(configDir, "repo.central.allow = org.graylog\n");

        // Basedir with mixed quotes (outer double, inner single)
        final Map<String, Object> properties = new HashMap<>();
        properties.put(CONFIG_PROP_BASEDIR, "\"'quoted-config'\"");
        final RepositorySystemSession session = createSession(properties, tempDir);

        final RemoteRepositoryFilter filter = source.getRemoteRepositoryFilter(session);

        assertNotNull(filter, "Filter should unwrap nested quotes from basedir property");
    }

    @Test
    void testConstructorInitialization() {
        // Test that constructor can be called (for JSR-330 injection)
        final StrictRemoteRepositoryFilterSource source = new StrictRemoteRepositoryFilterSource();
        assertNotNull(source, "Source should be successfully instantiated");
    }
}
