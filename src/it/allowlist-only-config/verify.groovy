// Verify that allowlist-only configuration works correctly
def buildLog = new File(basedir, "build.log")
assert buildLog.exists() : "build.log does not exist"

def logText = buildLog.text

// Check that the build was successful
assert logText.contains("BUILD SUCCESS") : "Build did not succeed"

// Verify that allowed dependencies were resolved
assert logText.contains("slf4j-api") : "slf4j-api should be allowed and resolved"
assert logText.contains("junit-jupiter-api") : "junit-jupiter-api should be allowed and resolved"
assert logText.contains("commons-io") : "commons-io should be allowed and resolved"

println "✓ Allowlist-only configuration test passed"
return true
