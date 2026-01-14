// Verify that empty allow configuration blocks all dependencies (fail-secure)
def buildLog = new File(basedir, "build.log")
assert buildLog.exists() : "build.log does not exist"

def logText = buildLog.text

// Check that the build failed as expected
assert logText.contains("BUILD FAILURE") : "Build should have failed (no artifacts allowed)"

// Verify that slf4j-api was blocked (not resolved)
// The build should fail due to dependency resolution failure
assert logText.contains("Could not find artifact") || logText.contains("resolution failed") || logText.contains("not allowed") : "Build should fail due to artifact not being allowed"

println "✓ Empty allow configuration test passed (correctly blocked all dependencies)"
return true
