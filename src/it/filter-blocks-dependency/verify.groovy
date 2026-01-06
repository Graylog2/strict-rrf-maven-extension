// Verify that the filter blocked the dependency and build failed
def buildLog = new File(basedir, "build.log")
assert buildLog.exists() : "build.log does not exist"

def logText = buildLog.text

// Check that the build FAILED (this is expected)
assert logText.contains("BUILD FAILURE") : "Build should have failed but succeeded"

// Check that the failure was related to dependency resolution for guava
assert logText.contains("guava") || logText.contains("com.google.guava") :
    "Build failure should mention guava"

// The filter should have rejected the artifact
// Look for typical Maven resolution failure messages or strict filter rejection
assert logText.contains("Could not") || logText.contains("Failed") ||
       logText.contains("Unable") || logText.contains("not allowed") || logText.contains("filtered") :
    "Build failure should indicate resolution problem or filter rejection"

println "✓ Filter blocks dependency test passed (build correctly failed)"
return true
