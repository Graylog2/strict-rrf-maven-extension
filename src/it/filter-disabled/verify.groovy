// Verify that when filter is disabled, everything works normally
def buildLog = new File(basedir, "build.log")
assert buildLog.exists() : "build.log does not exist"

def logText = buildLog.text

// Check that the build was successful (even though guava is not in allow list)
assert logText.contains("BUILD SUCCESS") : "Build should succeed when filter is disabled"

// Check that guava was resolved successfully
assert logText.contains("guava") || logText.contains("com.google.guava") :
    "guava was not resolved"

println "✓ Filter disabled (fail-safe) test passed"
return true
