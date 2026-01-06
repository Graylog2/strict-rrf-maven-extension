// Verify that coordinate patterns work correctly
def buildLog = new File(basedir, "build.log")
assert buildLog.exists() : "build.log does not exist"

def logText = buildLog.text

// Check that the build was successful
assert logText.contains("BUILD SUCCESS") : "Build did not succeed"

// Check that both dependencies were resolved
assert logText.contains("slf4j-api") : "slf4j-api was not resolved"
assert logText.contains("junit") || logText.contains("junit-4.13.2.jar") : "junit was not resolved"

println "✓ Coordinate patterns test passed"
return true
