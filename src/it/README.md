# Integration Tests

This directory contains integration tests for the Strict Remote Repository Filter Maven Extension using the Maven Invoker Plugin.

## Test Structure

Each test is a complete Maven project in its own directory with:
- `pom.xml` - Maven project file with dependencies
- `.mvn/extensions.xml` - Registers the extension
- `.mvn/maven.config` - Maven configuration (enables/disables filter)
- `.mvn/remoteRepositoryFilters/strict.properties` - Filter configuration
- `verify.groovy` - Post-build verification script
- `invoker.properties` (optional) - Test-specific settings

## Available Tests

### 1. basic-groupid-allow
Tests basic extension loading and groupId-only pattern matching.
- **Config**: Allows everything (`*`)
- **Expected**: Build succeeds, dependencies resolve

### 2. coordinate-patterns
Tests coordinate pattern matching (groupId:artifactId).
- **Config**: Allows everything (`*`)
- **Expected**: Build succeeds, verifies coordinate patterns load correctly

### 3. allow-deny-rules
Tests that deny rules override allow rules.
- **Config**: Allow `*`, deny `com.google.guava:*`
- **Dependencies**: Uses `org.slf4j:slf4j-api` (allowed)
- **Expected**: Build succeeds (guava not used)

### 4. filter-blocks-dependency
Tests that the filter actually blocks dependencies (default deny behavior).
- **Config**: Allows Maven infrastructure but NOT `com.google*`
- **Dependencies**: Uses `com.google.guava:guava` (blocked)
- **Expected**: Build FAILS (dependency blocked by filter)
- **Note**: `invoker.properties` sets `invoker.buildResult=failure`

### 5. filter-disabled
Tests fail-safe behavior when filter is disabled.
- **Config**: Very restrictive (only allows `org.slf4j`)
- **Maven Config**: Sets `enabled=false`
- **Dependencies**: Uses `com.google.guava:guava` (would be blocked if filter were enabled)
- **Expected**: Build succeeds (filter is disabled, so no blocking occurs)

## Running Integration Tests

```bash
# Run all tests (unit + integration)
mvn clean verify

# Run only integration tests
mvn invoker:install invoker:run

# Run integration tests with Maven debugging
mvn invoker:install invoker:run -X

# Skip integration tests
mvn clean install -DskipITs
```

## Test Execution Flow

1. **invoker:install** - Installs the extension JAR to `target/local-repo/`
2. **invoker:run** - For each test:
   - Copies test project to `target/it/<test-name>/`
   - Replaces `@project.version@` placeholders
   - Runs `mvn dependency:resolve` in test project
   - Executes `verify.groovy` script
   - Records pass/fail

## Verification Scripts

Each `verify.groovy` script:
- Reads `build.log` from the test execution
- Checks for expected build result (SUCCESS or FAILURE)
- Verifies specific dependencies were resolved/blocked
- Returns `true` for pass, throws assertion for fail

## Adding New Tests

1. Create new directory: `src/it/my-new-test/`
2. Add `pom.xml` with test dependencies
3. Create `.mvn/extensions.xml` (copy from existing test)
4. Create `.mvn/maven.config` with filter settings
5. Create `.mvn/remoteRepositoryFilters/strict.properties` with filter rules
6. Add `verify.groovy` to check test results
7. (Optional) Add `invoker.properties` for special settings

## Common Patterns

**Allow everything:**
```properties
repo.central.allow = *
```

**Allow specific groupIds:**
```properties
repo.central.allow = org.slf4j,org.apache.maven
```

**Allow with deny override:**
```properties
repo.central.allow = *
repo.central.deny = com.google.guava:*
```

**Coordinate patterns:**
```properties
repo.central.allow = org.slf4j:slf4j-api,junit:junit,org.hamcrest:*
```

## Troubleshooting

If tests fail unexpectedly:

1. Check `target/it/<test-name>/build.log` for Maven output
2. Verify filter configuration in `strict.properties`
3. Ensure Maven infrastructure dependencies are allowed
4. Run with `-X` for debug output
5. Check that `@project.version@` was replaced correctly

## Notes

- Tests use `dependency:resolve` goal to force dependency resolution
- Maven infrastructure requires many dependencies (org.apache*, org.codehaus*, etc.)
- Test local repository is isolated in `target/local-repo/`
- Tests run in parallel by default
- Each test gets a fresh Maven project directory
