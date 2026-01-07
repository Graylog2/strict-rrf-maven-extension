/*
 * Verification script for quoted-basedir-path integration test.
 * Verifies that:
 * 1. The build succeeded (quotes were properly stripped from basedir)
 * 2. The strict filter configuration was loaded
 * 3. Allowed dependencies were resolved
 */

def buildLog = new File(basedir, 'build.log').text

// Verify the build succeeded
assert buildLog.contains('BUILD SUCCESS'),
    'Build should succeed when basedir has quotes and they are properly stripped'

// Verify the filter was active and loaded configuration
assert buildLog.contains('Strict filter is active with configuration from:'),
    'Filter should be active and load configuration'

// Verify the configuration was loaded from the correct path (without quotes)
// The path should be resolved to .mvn/remoteRepositoryFilters
assert buildLog.contains('.mvn/remoteRepositoryFilters'),
    'Configuration should be loaded from .mvn/remoteRepositoryFilters'

// Verify that commons-io was allowed (it's in the allow list)
assert buildLog.contains('commons-io:commons-io:jar:2.11.0'),
    'commons-io dependency should be allowed'

// Make sure there's no error about configuration file not existing
// This would happen if quotes weren't stripped properly
assert !buildLog.contains('Configuration file does not exist:') ||
       !buildLog.contains('"${session.rootDirectory}'),
    'Should not have literal quotes in the resolved path'

// Verify no warnings about blocking ALL artifacts (which would happen if config wasn't loaded)
assert !buildLog.contains('BLOCKING ALL ARTIFACTS'),
    'Should not block all artifacts when valid config exists'

println "✓ Quoted basedir path test passed"
println "  - Build succeeded with quoted path in configuration"
println "  - Strict filter loaded configuration correctly"
println "  - Quotes were properly stripped from basedir"
