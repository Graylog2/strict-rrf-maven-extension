package org.graylog.maven.resolver.filter.strict;

import org.eclipse.aether.spi.connector.filter.RemoteRepositoryFilter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link SimpleFilterResult}.
 */
class SimpleFilterResultTest {

    @Test
    void testAcceptedReturnsAcceptedResult() {
        final RemoteRepositoryFilter.Result result = SimpleFilterResult.accepted();

        assertNotNull(result, "Result should not be null");
        assertTrue(result.isAccepted(), "Result should be accepted");
    }

    @Test
    void testAcceptedReturnsSingletonInstance() {
        final RemoteRepositoryFilter.Result result1 = SimpleFilterResult.accepted();
        final RemoteRepositoryFilter.Result result2 = SimpleFilterResult.accepted();

        assertSame(result1, result2,
                "accepted() should return the same singleton instance for memory efficiency");
    }

    @Test
    void testAcceptedReasoningIsEmpty() {
        final RemoteRepositoryFilter.Result result = SimpleFilterResult.accepted();

        assertNotNull(result.reasoning(), "Reasoning should not be null");
        assertEquals("", result.reasoning(),
                "Accepted result should have empty reasoning");
    }

    @Test
    void testRejectedReturnsRejectedResult() {
        final String reason = "Test rejection reason";
        final RemoteRepositoryFilter.Result result = SimpleFilterResult.rejected(reason);

        assertNotNull(result, "Result should not be null");
        assertFalse(result.isAccepted(), "Result should be rejected");
    }

    @Test
    void testRejectedReasoningContainsMessage() {
        final String reason = "Artifact not allowed from repository";
        final RemoteRepositoryFilter.Result result = SimpleFilterResult.rejected(reason);

        assertNotNull(result.reasoning(), "Reasoning should not be null");
        assertEquals(reason, result.reasoning(),
                "Rejected result should contain the provided reasoning");
    }

    @Test
    void testRejectedCreatesNewInstance() {
        final RemoteRepositoryFilter.Result result1 = SimpleFilterResult.rejected("Reason 1");
        final RemoteRepositoryFilter.Result result2 = SimpleFilterResult.rejected("Reason 2");

        // Results should not be the same instance (rejected results are not singletons)
        // Each rejection may have a different reason
        // Intentionally using assertNotSame to verify different object instances
        assertNotEquals(result1, result2, "Different rejected results should not be equal");
    }

    @Test
    void testToStringForAccepted() {
        final RemoteRepositoryFilter.Result result = SimpleFilterResult.accepted();

        final String toString = result.toString();

        assertNotNull(toString, "toString should not be null");
        assertEquals("ACCEPTED", toString,
                "Accepted result toString should be 'ACCEPTED'");
    }

    @Test
    void testToStringForRejected() {
        final String reason = "Artifact com.example:test:1.0 not allowed";
        final RemoteRepositoryFilter.Result result = SimpleFilterResult.rejected(reason);

        final String toString = result.toString();

        assertNotNull(toString, "toString should not be null");
        assertTrue(toString.startsWith("REJECTED:"),
                "Rejected result toString should start with 'REJECTED:'");
        assertTrue(toString.contains(reason),
                "Rejected result toString should contain the reason");
    }

    @Test
    void testRejectedWithNullReasoning() {
        final RemoteRepositoryFilter.Result result = SimpleFilterResult.rejected(null);

        assertNotNull(result, "Result should not be null");
        assertFalse(result.isAccepted(), "Result should be rejected");
        assertEquals("", result.reasoning(),
                "Reasoning should be empty string when null is provided");
    }

    @Test
    void testRejectedWithEmptyReasoning() {
        final RemoteRepositoryFilter.Result result = SimpleFilterResult.rejected("");

        assertNotNull(result, "Result should not be null");
        assertFalse(result.isAccepted(), "Result should be rejected");
        assertEquals("", result.reasoning(),
                "Reasoning should be empty string");
    }

    @Test
    void testRejectedWithLongReasoning() {
        final String longReason = "This is a very long rejection reason that contains a lot of " +
                "information about why the artifact was rejected from the repository, including " +
                "the groupId, artifactId, version, and repository ID. It should still work correctly.";
        final RemoteRepositoryFilter.Result result = SimpleFilterResult.rejected(longReason);

        assertNotNull(result, "Result should not be null");
        assertFalse(result.isAccepted(), "Result should be rejected");
        assertEquals(longReason, result.reasoning(),
                "Long reasoning should be preserved exactly");
    }

    @Test
    void testMultipleRejectedInstancesHaveDifferentReasons() {
        final String reason1 = "Artifact A not allowed";
        final String reason2 = "Artifact B not allowed";

        final RemoteRepositoryFilter.Result result1 = SimpleFilterResult.rejected(reason1);
        final RemoteRepositoryFilter.Result result2 = SimpleFilterResult.rejected(reason2);

        assertEquals(reason1, result1.reasoning(), "First result should have first reason");
        assertEquals(reason2, result2.reasoning(), "Second result should have second reason");
    }

    @Test
    void testAcceptedAndRejectedAreDifferentTypes() {
        final RemoteRepositoryFilter.Result accepted = SimpleFilterResult.accepted();
        final RemoteRepositoryFilter.Result rejected = SimpleFilterResult.rejected("Test");

        assertTrue(accepted.isAccepted(), "Accepted should be accepted");
        assertFalse(rejected.isAccepted(), "Rejected should not be accepted");

        assertNotEquals(accepted, rejected, "Accepted and rejected results should not be equal");
    }
}
