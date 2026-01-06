// Verify that the build succeeded and slf4j dependency was resolved
def buildLog = new File(basedir, "build.log")
assert buildLog.exists() : "build.log does not exist"

def logText = buildLog.text

// Check that the build was successful
assert logText.contains("BUILD SUCCESS") : "Build did not succeed"

// Check that slf4j was downloaded (or already cached)
// We check for typical Maven resolution messages
assert logText.contains("slf4j-api") : "slf4j-api was not resolved"

println "✓ Basic groupId-only allow pattern test passed"
return true
