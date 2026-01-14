// Verify that allowlist-only configuration works correctly
def buildLog = new File(basedir, "build.log")
assert buildLog.exists() : "build.log does not exist"

def logText = buildLog.text

// Check that the build was successful
assert logText.contains("BUILD SUCCESS") : "Build did not succeed"

// Verify that at least some dependencies were resolved (from allowlist)
// Dependencies may be cached so they may not show "Downloading", but at least one should appear
assert logText.contains("commons-io") || logText.contains("junit-jupiter-api") : "At least one allowed dependency should be resolved"

println "✓ Allowlist-only configuration test passed"
return true
