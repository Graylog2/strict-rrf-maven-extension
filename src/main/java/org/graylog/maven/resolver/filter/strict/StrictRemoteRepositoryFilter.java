package org.graylog.maven.resolver.filter.strict;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.connector.filter.RemoteRepositoryFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of {@link RemoteRepositoryFilter} that applies strict filtering rules
 * based on configuration files.
 *
 * <p>This filter delegates to {@link StrictFilterConfiguration} to determine whether
 * artifacts and metadata should be accepted from specific remote repositories.
 */
public class StrictRemoteRepositoryFilter implements RemoteRepositoryFilter {

    private static final Logger logger = LoggerFactory.getLogger(StrictRemoteRepositoryFilter.class);
    private final StrictFilterConfiguration config;

    /**
     * Creates a new filter with the specified configuration.
     *
     * @param config the filter configuration
     */
    public StrictRemoteRepositoryFilter(StrictFilterConfiguration config) {
        this.config = config;
    }

    @Override
    public Result acceptArtifact(RemoteRepository repository, Artifact artifact) {
        logger.debug("Checking artifact: {}:{}:{} from repository: {}",
                artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion(), repository.getId());

        final boolean accepted = config.isArtifactAllowed(repository.getId(), artifact);

        if (accepted) {
            logger.debug("Accepting artifact: {}:{}:{} from {}",
                    artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion(), repository.getId());
            return SimpleFilterResult.accepted();
        } else {
            logger.debug("Rejecting artifact: {}:{}:{} from {} (filtered by strict rules)",
                    artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion(), repository.getId());
            return SimpleFilterResult.rejected(
                    String.format("Artifact %s:%s:%s not allowed from repository %s by strict filter",
                            artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion(), repository.getId())
            );
        }
    }

    @Override
    public Result acceptMetadata(RemoteRepository repository, Metadata metadata) {
        logger.debug("Checking metadata: {} from repository: {}",
                metadata, repository.getId());

        final boolean accepted = config.isMetadataAllowed(repository.getId(), metadata);

        if (accepted) {
            logger.debug("Accepting metadata from {}", repository.getId());
            return SimpleFilterResult.accepted();
        } else {
            logger.debug("Rejecting metadata {} from {} (filtered by strict rules)",
                    metadata, repository.getId());
            return SimpleFilterResult.rejected(
                    String.format("Metadata %s not allowed from repository %s by strict filter",
                            metadata, repository.getId())
            );
        }
    }
}
