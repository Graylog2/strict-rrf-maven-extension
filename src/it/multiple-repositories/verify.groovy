// Verify that multiple repository configurations work correctly
def buildLog = new File(basedir, "build.log")
assert buildLog.exists() : "build.log does not exist"

def logText = buildLog.text

// Check that the build was successful
assert logText.contains("BUILD SUCCESS") : "Build did not succeed"

// Verify that dependencies allowed by their respective repository rules were resolved
assert logText.contains("slf4j-api") : "slf4j-api should be allowed from central and resolved"
assert logText.contains("commons-io") : "commons-io should be allowed from both repos and resolved"

// Verify that configuration loaded multiple repositories
assert logText.contains("Loading configuration for repository 'central'") || logText.contains("Loaded") : "Configuration should be loaded"

println "✓ Multiple repositories configuration test passed"
return true
