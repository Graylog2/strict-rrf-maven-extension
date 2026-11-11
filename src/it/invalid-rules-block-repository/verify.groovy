// Verify that invalid rules block the affected repository (fail-secure) instead of being ignored
def buildLog = new File(basedir, "build.log")
assert buildLog.exists() : "build.log does not exist"

def logText = buildLog.text

// Check that the build failed as expected
assert logText.contains("BUILD FAILURE") : "Build should have failed (central is blocked by an invalid rule)"

// Both invalid rules must be reported, not silently dropped
assert logText.contains("Invalid pattern(s) [com.google.guava:guava:31.0] for property 'repo.central.deny'") :
    "Invalid deny pattern should be reported"
assert logText.contains("Duplicate property 'repo.other.allow'") :
    "Duplicate key should be reported"

// Central allows '*', so a rejection proves the invalid deny rule blocked the whole repository.
// Depending on the local cache, either the dependency plugin or slf4j-api is the first artifact rejected.
assert logText.contains("not allowed from repository central by strict filter") :
    "Artifacts from central should be rejected by the strict filter"

println "✓ Invalid rules test passed (repository blocked instead of ignoring the rules)"
return true
