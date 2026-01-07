package org.graylog.maven.resolver.filter.strict;

import org.eclipse.aether.spi.connector.filter.RemoteRepositoryFilter;

/**
 * Simple implementation of {@link RemoteRepositoryFilter.Result}.
 * This class represents the outcome of a repository filter decision,
 * indicating whether an artifact or metadata should be accepted or rejected.
 */
public class SimpleFilterResult implements RemoteRepositoryFilter.Result {

    private static final SimpleFilterResult ACCEPTED = new SimpleFilterResult(true, null);

    private final boolean accepted;
    private final String reasoning;

    private SimpleFilterResult(boolean accepted, String reasoning) {
        this.accepted = accepted;
        this.reasoning = reasoning;
    }

    /**
     * Creates a result indicating acceptance.
     * Returns a singleton instance to avoid unnecessary object creation.
     *
     * @return an accepted filter result
     */
    public static SimpleFilterResult accepted() {
        return ACCEPTED;
    }

    /**
     * Creates a result indicating rejection with a reason.
     *
     * @param reasoning the reason for rejection
     * @return a rejected filter result with reasoning
     */
    public static SimpleFilterResult rejected(String reasoning) {
        return new SimpleFilterResult(false, reasoning);
    }

    @Override
    public boolean isAccepted() {
        return accepted;
    }

    @Override
    public String reasoning() {
        return reasoning != null ? reasoning : "";
    }

    @Override
    public String toString() {
        return accepted ? "ACCEPTED" : "REJECTED: " + reasoning;
    }
}
