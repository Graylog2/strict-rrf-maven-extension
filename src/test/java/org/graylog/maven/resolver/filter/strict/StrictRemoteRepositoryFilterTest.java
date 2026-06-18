package org.graylog.maven.resolver.filter.strict;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.metadata.DefaultMetadata;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.filter.RemoteRepositoryFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link StrictRemoteRepositoryFilter}.
 */
class StrictRemoteRepositoryFilterTest {

    /**
     * Creates a test remote repository.
     */
    private RemoteRepository createRepository(String id) {
        return new RemoteRepository.Builder(id, "default", "https://example.com/repo").build();
    }

    @Test
    void testAcceptArtifactReturnsAcceptedResult(@TempDir Path tempDir) throws Exception {
        StrictPropertiesTestHelper.writeStrictProperties(tempDir, "repo.central.allow = org.graylog\n");

        final StrictFilterConfiguration config = StrictFilterConfiguration.load(
                tempDir.toString(),
                tempDir
        );
        final StrictRemoteRepositoryFilter filter = new StrictRemoteRepositoryFilter(config);

        final RemoteRepository repository = createRepository("central");
        final Artifact artifact = new DefaultArtifact("org.graylog:server:1.0");

        final RemoteRepositoryFilter.Result result = filter.acceptArtifact(repository, artifact);

        assertNotNull(result, "Result should not be null");
        assertTrue(result.isAccepted(), "Artifact should be accepted");
        assertNotNull(result.reasoning(), "Reasoning should not be null");
    }

    @Test
    void testAcceptArtifactReturnsRejectedResult(@TempDir Path tempDir) throws Exception {
        StrictPropertiesTestHelper.writeStrictProperties(tempDir, "repo.central.allow = org.graylog\n");

        final StrictFilterConfiguration config = StrictFilterConfiguration.load(
                tempDir.toString(),
                tempDir
        );
        final StrictRemoteRepositoryFilter filter = new StrictRemoteRepositoryFilter(config);

        final RemoteRepository repository = createRepository("central");
        final Artifact artifact = new DefaultArtifact("com.example:app:1.0");

        final RemoteRepositoryFilter.Result result = filter.acceptArtifact(repository, artifact);

        assertNotNull(result, "Result should not be null");
        assertFalse(result.isAccepted(), "Artifact should be rejected");
        assertNotNull(result.reasoning(), "Reasoning should not be null");
        assertTrue(result.reasoning().contains("not allowed"),
                "Reasoning should explain why artifact was rejected");
        assertTrue(result.reasoning().contains("com.example"),
                "Reasoning should contain groupId");
        assertTrue(result.reasoning().contains("app"),
                "Reasoning should contain artifactId");
    }

    @Test
    void testAcceptArtifactWithDenyRule(@TempDir Path tempDir) throws Exception {
        StrictPropertiesTestHelper.writeStrictProperties(tempDir, """
                repo.central.allow = org.graylog*
                repo.central.deny = org.graylog.internal*
                """);

        final StrictFilterConfiguration config = StrictFilterConfiguration.load(
                tempDir.toString(),
                tempDir
        );
        final StrictRemoteRepositoryFilter filter = new StrictRemoteRepositoryFilter(config);

        final RemoteRepository repository = createRepository("central");

        // Allowed artifact
        final Artifact allowedArtifact = new DefaultArtifact("org.graylog:server:1.0");
        final RemoteRepositoryFilter.Result allowedResult = filter.acceptArtifact(repository, allowedArtifact);
        assertTrue(allowedResult.isAccepted(), "Non-internal artifact should be accepted");

        // Denied artifact
        final Artifact deniedArtifact = new DefaultArtifact("org.graylog.internal:utils:1.0");
        final RemoteRepositoryFilter.Result deniedResult = filter.acceptArtifact(repository, deniedArtifact);
        assertFalse(deniedResult.isAccepted(), "Internal artifact should be denied");
        assertTrue(deniedResult.reasoning().contains("not allowed"),
                "Reasoning should explain denial");
    }

    @Test
    void testAcceptArtifactNoConfigForRepository(@TempDir Path tempDir) throws Exception {
        StrictPropertiesTestHelper.writeStrictProperties(tempDir, "repo.central.allow = org.graylog\n");

        final StrictFilterConfiguration config = StrictFilterConfiguration.load(
                tempDir.toString(),
                tempDir
        );
        final StrictRemoteRepositoryFilter filter = new StrictRemoteRepositoryFilter(config);

        // Request artifact from repository with no configuration (fail-secure)
        final RemoteRepository repository = createRepository("other-repo");
        final Artifact artifact = new DefaultArtifact("org.graylog:server:1.0");

        final RemoteRepositoryFilter.Result result = filter.acceptArtifact(repository, artifact);

        assertFalse(result.isAccepted(),
                "Artifact should be rejected when no config exists for repository (fail-secure)");
        assertTrue(result.reasoning().contains("not allowed"),
                "Reasoning should explain rejection");
    }

    @Test
    void testAcceptMetadataReturnsAcceptedResult(@TempDir Path tempDir) throws Exception {
        StrictPropertiesTestHelper.writeStrictProperties(tempDir, "repo.central.allow = org.graylog\n");

        final StrictFilterConfiguration config = StrictFilterConfiguration.load(
                tempDir.toString(),
                tempDir
        );
        final StrictRemoteRepositoryFilter filter = new StrictRemoteRepositoryFilter(config);

        final RemoteRepository repository = createRepository("central");
        final Metadata metadata = new DefaultMetadata("org.graylog", "maven-metadata.xml",
                Metadata.Nature.RELEASE_OR_SNAPSHOT);

        final RemoteRepositoryFilter.Result result = filter.acceptMetadata(repository, metadata);

        assertNotNull(result, "Result should not be null");
        assertTrue(result.isAccepted(), "Metadata should be accepted");
        assertNotNull(result.reasoning(), "Reasoning should not be null");
    }

    @Test
    void testAcceptMetadataReturnsRejectedResult(@TempDir Path tempDir) throws Exception {
        StrictPropertiesTestHelper.writeStrictProperties(tempDir, "repo.central.allow = org.graylog\n");

        final StrictFilterConfiguration config = StrictFilterConfiguration.load(
                tempDir.toString(),
                tempDir
        );
        final StrictRemoteRepositoryFilter filter = new StrictRemoteRepositoryFilter(config);

        final RemoteRepository repository = createRepository("central");
        final Metadata metadata = new DefaultMetadata("com.example", "maven-metadata.xml",
                Metadata.Nature.RELEASE_OR_SNAPSHOT);

        final RemoteRepositoryFilter.Result result = filter.acceptMetadata(repository, metadata);

        assertNotNull(result, "Result should not be null");
        assertFalse(result.isAccepted(), "Metadata should be rejected");
        assertNotNull(result.reasoning(), "Reasoning should not be null");
        assertTrue(result.reasoning().contains("not allowed"),
                "Reasoning should explain why metadata was rejected");
        assertTrue(result.reasoning().contains("com.example"),
                "Reasoning should contain groupId");
    }

    @Test
    void testAcceptMetadataWithoutGroupId(@TempDir Path tempDir) throws Exception {
        StrictPropertiesTestHelper.writeStrictProperties(tempDir, "repo.central.allow = org.graylog\n");

        final StrictFilterConfiguration config = StrictFilterConfiguration.load(
                tempDir.toString(),
                tempDir
        );
        final StrictRemoteRepositoryFilter filter = new StrictRemoteRepositoryFilter(config);

        final RemoteRepository repository = createRepository("central");
        // Repository-level metadata without groupId
        final Metadata metadata = new DefaultMetadata("maven-metadata.xml", Metadata.Nature.RELEASE_OR_SNAPSHOT);

        final RemoteRepositoryFilter.Result result = filter.acceptMetadata(repository, metadata);

        assertTrue(result.isAccepted(),
                "Repository-level metadata without groupId should always be accepted");
    }

    @Test
    void testAcceptMetadataNoConfigForRepository(@TempDir Path tempDir) throws Exception {
        StrictPropertiesTestHelper.writeStrictProperties(tempDir, "repo.central.allow = org.graylog\n");

        final StrictFilterConfiguration config = StrictFilterConfiguration.load(
                tempDir.toString(),
                tempDir
        );
        final StrictRemoteRepositoryFilter filter = new StrictRemoteRepositoryFilter(config);

        // Request metadata from repository with no configuration (fail-secure)
        final RemoteRepository repository = createRepository("other-repo");
        final Metadata metadata = new DefaultMetadata("org.graylog", "maven-metadata.xml",
                Metadata.Nature.RELEASE_OR_SNAPSHOT);

        final RemoteRepositoryFilter.Result result = filter.acceptMetadata(repository, metadata);

        assertFalse(result.isAccepted(),
                "Metadata should be rejected when no config exists for repository (fail-secure)");
        assertTrue(result.reasoning().contains("not allowed"),
                "Reasoning should explain rejection");
    }

    @Test
    void testRejectMetadataWithoutGroupIdForUnconfiguredRepository(@TempDir Path tempDir) throws Exception {
        StrictPropertiesTestHelper.writeStrictProperties(tempDir, "repo.central.allow = org.graylog\n");

        final StrictFilterConfiguration config = StrictFilterConfiguration.load(
                tempDir.toString(),
                tempDir
        );
        final StrictRemoteRepositoryFilter filter = new StrictRemoteRepositoryFilter(config);

        // Repository-level metadata (no groupId) from a repository with no configuration must be
        // rejected (fail-secure) - it should not bypass the filter just because it lacks a groupId.
        final RemoteRepository repository = createRepository("other-repo");
        final Metadata metadata = new DefaultMetadata("maven-metadata.xml", Metadata.Nature.RELEASE_OR_SNAPSHOT);

        final RemoteRepositoryFilter.Result result = filter.acceptMetadata(repository, metadata);

        assertFalse(result.isAccepted(),
                "Repository-level metadata must be rejected for an unconfigured repository (fail-secure)");
    }

    @Test
    void testReasoningMessageFormat(@TempDir Path tempDir) throws Exception {
        StrictPropertiesTestHelper.writeStrictProperties(tempDir, "repo.central.allow = org.graylog\n");

        final StrictFilterConfiguration config = StrictFilterConfiguration.load(
                tempDir.toString(),
                tempDir
        );
        final StrictRemoteRepositoryFilter filter = new StrictRemoteRepositoryFilter(config);

        final RemoteRepository repository = createRepository("central");
        final Artifact artifact = new DefaultArtifact("com.example:test-app:2.0.0");

        final RemoteRepositoryFilter.Result result = filter.acceptArtifact(repository, artifact);

        final String reasoning = result.reasoning();
        assertTrue(reasoning.contains("com.example"), "Should contain groupId");
        assertTrue(reasoning.contains("test-app"), "Should contain artifactId");
        assertTrue(reasoning.contains("2.0.0"), "Should contain version");
        assertTrue(reasoning.contains("central"), "Should contain repository ID");
        assertTrue(reasoning.contains("strict filter"), "Should mention the filter");
    }

    @Test
    void testEmptyConfiguration(@TempDir Path tempDir) {
        // No configuration file - fail-secure mode
        final StrictFilterConfiguration config = StrictFilterConfiguration.load(
                tempDir.toString(),
                tempDir
        );
        final StrictRemoteRepositoryFilter filter = new StrictRemoteRepositoryFilter(config);

        final RemoteRepository repository = createRepository("central");
        final Artifact artifact = new DefaultArtifact("org.graylog:server:1.0");

        final RemoteRepositoryFilter.Result result = filter.acceptArtifact(repository, artifact);

        assertFalse(result.isAccepted(),
                "All artifacts should be blocked when no configuration exists (fail-secure)");
    }

    @Test
    void testMultipleRepositoriesInFilter(@TempDir Path tempDir) throws Exception {
        StrictPropertiesTestHelper.writeStrictProperties(tempDir, """
                repo.central.allow = org.graylog
                repo.company.allow = com.company
                """);

        final StrictFilterConfiguration config = StrictFilterConfiguration.load(
                tempDir.toString(),
                tempDir
        );
        final StrictRemoteRepositoryFilter filter = new StrictRemoteRepositoryFilter(config);

        // Test central repository
        final RemoteRepository centralRepo = createRepository("central");
        final Artifact graylogArtifact = new DefaultArtifact("org.graylog:server:1.0");
        assertTrue(filter.acceptArtifact(centralRepo, graylogArtifact).isAccepted(),
                "Graylog artifact should be accepted from central");

        final Artifact companyArtifact = new DefaultArtifact("com.company:app:1.0");
        assertFalse(filter.acceptArtifact(centralRepo, companyArtifact).isAccepted(),
                "Company artifact should be rejected from central");

        // Test company repository
        final RemoteRepository companyRepo = createRepository("company");
        assertTrue(filter.acceptArtifact(companyRepo, companyArtifact).isAccepted(),
                "Company artifact should be accepted from company repo");

        assertFalse(filter.acceptArtifact(companyRepo, graylogArtifact).isAccepted(),
                "Graylog artifact should be rejected from company repo");
    }
}
