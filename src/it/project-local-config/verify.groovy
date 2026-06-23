// Verify the build succeeded using project-local config that was auto-discovered
// from .mvn/remoteRepositoryFilters (no basedir property was set).
def buildLog = new File(basedir, "build.log")
assert buildLog.exists() : "build.log does not exist"

def logText = buildLog.text

assert logText.contains("BUILD SUCCESS") : "Build did not succeed"
assert logText.contains("slf4j-api") : "slf4j-api was not resolved"

println "✓ Project-local config auto-discovery test passed"
return true
