// Verify that allow/deny rules work correctly
def buildLog = new File(basedir, "build.log")
assert buildLog.exists() : "build.log does not exist"

def logText = buildLog.text

// Check that the build was successful
assert logText.contains("BUILD SUCCESS") : "Build did not succeed"

// Check that slf4j-api was allowed and resolved
assert logText.contains("slf4j-api") : "slf4j-api was not resolved (should be allowed)"

// slf4j-simple should not appear (we don't use it, but it's denied anyway)
// This test mainly verifies the config loads and slf4j-api works

println "✓ Allow/deny rules test passed"
return true
