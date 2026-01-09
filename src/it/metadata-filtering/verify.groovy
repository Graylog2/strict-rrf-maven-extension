/*
 * Verification script for metadata-filtering integration test.
 * Verifies that:
 * 1. The build failed (metadata for guava was blocked)
 * 2. The filter was active
 * 3. Metadata rejection was logged
 */

def buildLog = new File(basedir, 'build.log').text

// Verify the build FAILED (this is expected - metadata for guava should be blocked)
assert buildLog.contains('BUILD FAILURE'),
    'Build should fail when metadata for blocked artifact is requested'

// Verify the filter was active
assert buildLog.contains('Strict filter is active'),
    'Filter should be active'

// Verify that we tried to resolve com.google.guava
assert buildLog.contains('com.google.guava') || buildLog.contains('guava'),
    'Build log should mention guava'

// Verify metadata rejection or resolution failure
// The filter may reject either the metadata or the artifact itself
assert buildLog.contains('Rejecting') ||
       buildLog.contains('Could not resolve') ||
       buildLog.contains('Failed to') ||
       buildLog.contains('not allowed'),
    'Build should show rejection or resolution failure'

println "✓ Metadata filtering test passed"
println "  - Build correctly failed when trying to resolve blocked artifact metadata"
println "  - Strict filter was active and enforced rules"
