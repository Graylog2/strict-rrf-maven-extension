# Agent Guide for strict-rrf-maven-extension

This document provides coding guidelines and conventions for AI coding agents working on the Strict Remote Repository Filter Maven Extension project.

## Project Overview

This is a Maven extension that implements the Maven Resolver Remote Repository Filter SPI. It provides properties-based configuration for filtering artifacts and metadata from remote Maven repositories with a fail-secure design.

**Technology Stack:**
- Java 17 (source & target)
- Maven 3.9.0+ (required)
- JUnit Jupiter >= 6 for testing
- SLF4J for logging

## Build & Test Commands

### Basic Commands

```bash
# Clean and compile
./mvnw clean compile

# Run all unit tests
./mvnw clean test

# Run all tests (unit + integration)
./mvnw clean verify

# Run integration tests only
./mvnw clean install

# Package the extension
./mvnw clean package

# Install to local repository
./mvnw clean install -DskipTests
```

### Running Specific Tests

```bash
# Run a single unit test class
./mvnw test -Dtest=StrictRemoteRepositoryFilterTest

# Run a single test method
./mvnw test -Dtest=StrictRemoteRepositoryFilterTest#testAcceptArtifactReturnsAcceptedResult

# Run a specific integration test
./mvnw clean install -Dinvoker.test=basic-groupid-allow

# Run integration tests matching pattern
./mvnw clean install -Dinvoker.test=filter-*
```

### Code Quality & Verification

```bash
# Run PMD code analysis
./mvnw pmd:check

# Run enforcer plugin rules
./mvnw enforcer:enforce

# Generate Javadoc
./mvnw javadoc:jar

# Run all checks (compile + test + PMD)
./mvnw clean verify
```

### Debug Mode

```bash
# Enable Maven debug output
./mvnw clean test -X

# Enable debug logging for tests
./mvnw test -Dorg.slf4j.simpleLogger.defaultLogLevel=debug
```

## Code Style Guidelines

### Import Organization

Wildcard imports (e.g., `java.*`) are not allowed.

### Formatting & Style

- **Indentation:** 4 spaces (no tabs)
- **Line length:** No strict limit, but aim for readability (~120 chars recommended)
- **Braces:** Opening brace on same line, closing brace on new line
- **Access modifiers:** Always use explicit access modifiers (public/private/protected)
- **Final variables:** Use `final` for parameters and local variables when appropriate
- **Logger naming:** Use lowercase `logger` (not `LOG` or `LOGGER`)

### Naming Conventions

- **Classes:** PascalCase (e.g., `StrictRemoteRepositoryFilter`)
- **Methods:** camelCase (e.g., `isArtifactAllowed`)
- **Variables:** camelCase (e.g., `repositoryId`, `basedir`)
- **Constants:** UPPER_SNAKE_CASE (e.g., `CONFIG_PROP_ENABLED`, `DEFAULT_BASEDIR`)
- **Test methods:** Use descriptive names with underscores (e.g., `testExactGroupIdMatch`)
- **Logger constant:** Use lowercase `logger` (special exception to constant naming)

### Types & Nullability

- **Prefer immutable collections:** Use `Set.of()`, `Map.of()`, `List.of()`, and `Collections.unmodifiableX()`
- **Null handling:** Check for null explicitly; metadata groupId can be null
- **Type inference:** Use `var` sparingly; prefer explicit types for clarity
- **Generic types:** Always specify type parameters (no raw types)

### Error Handling

- **Logging:** Use SLF4J logger with appropriate levels:
  - `logger.error()` - Critical failures
  - `logger.warn()` - Configuration issues, validation failures
  - `logger.info()` - Important state changes, loaded configuration
  - `logger.debug()` - Detailed debugging information
- **Exceptions:** Catch specific exceptions rather than generic `Exception`
- **Resource handling:** Always use try-with-resources for I/O operations
- **Fail-secure principle:** When configuration is missing or invalid, deny access (return false/null)

### Documentation

- **Javadoc:** Required for all public classes and methods
- **Javadoc tags:** Use `@param`, `@return`, `@throws` appropriately
- **Code comments:** Use `//` for inline comments, explain "why" not "what"
- **Example blocks:** Use `<pre>` tags for code examples in Javadoc
- **HTML in Javadoc:** Use `<p>`, `<ul>`, `<li>`, `<strong>` for formatting

**Example:**
```java
/**
 * Determines if an artifact is allowed from the specified repository.
 *
 * <p>Uses allow/deny rules with glob pattern matching on both groupId and artifactId.
 * By default, everything is denied unless explicitly allowed.
 * Deny rules override allow rules.
 *
 * @param repositoryId the repository ID
 * @param artifact     the artifact to check
 * @return true if the artifact is allowed, false otherwise
 */
public boolean isArtifactAllowed(String repositoryId, Artifact artifact) {
    // Implementation
}
```

## Testing Guidelines

### Test Structure

- **Framework:** JUnit Jupiter >= 6
- **Test class naming:** `<ClassUnderTest>Test.java`
- **Test method naming:** Descriptive with underscores (e.g., `testEmptyAllowPatternsDenyAll`)
- **Assertions:** Use JUnit Jupiter assertions (`org.junit.jupiter.api.Assertions`)
- **Temp directories:** Use `@TempDir` annotation for file-based tests

### Test Organization

```java
@Test
void testMethodName() {
    // Arrange: Set up test data
    final RepositoryRule rule = new RepositoryRule(Set.of("org.graylog"), Set.of());
    
    // Act: Execute the method under test
    final boolean result = rule.isArtifactAllowed(artifact);
    
    // Assert: Verify the expected outcome
    assertTrue(result, "Should allow exact groupId match");
}
```

### Integration Tests

- Located in `src/it/<test-name>/`
- Each test has its own directory with `pom.xml` and `verify.groovy`
- Configuration files in `.mvn/remoteRepositoryFilters/strict.properties`
- Run via Maven Invoker Plugin

## Architecture Notes

### Core Components

1. **StrictRemoteRepositoryFilterSource** - SPI entry point with `@Named("strict")` annotation
2. **StrictRemoteRepositoryFilter** - Implements filtering logic
3. **StrictFilterConfiguration** - Loads and manages configuration
4. **RepositoryRule** - Encapsulates allow/deny patterns per repository
5. **SimpleFilterResult** - Result object for filter decisions

### Key Design Patterns

- **Dependency Injection:** Uses JSR-330 annotations (`@Named`, `@Inject`, `@Singleton`)
- **Immutability:** Configuration objects are immutable after construction
- **Fail-secure:** Missing/invalid configuration blocks all artifacts
- **Builder pattern:** Used for creating test repositories

### Configuration Properties

- `aether.remoteRepositoryFilter.strict.enabled` - Enable/disable filter (default: true)
- `aether.remoteRepositoryFilter.strict.basedir` - Config directory (default: `.remoteRepositoryFilters`)

### Pattern Matching

Supports two pattern types:
1. **GroupId-only:** `org.graylog` (exact), `org.graylog*` (wildcard)
2. **Coordinates:** `com.google:guava` (exact), `com.company:test-*` (wildcard)

Uses `org.apache.maven.shared.utils.io.SelectorUtils` for glob matching.

## PMD Rules

The project uses PMD for static analysis with a custom ruleset (`config/pmd-ruleset.xml`):

- **Enabled:** Most best practices, error-prone, and performance rules
- **Excluded:** Overly strict complexity metrics, opinionated style rules
- **Cognitive complexity limit:** 25 (increased for complex parsing methods)
- **Logger exception:** Lowercase `logger` is allowed as a constant name

## Common Tasks

### Debugging Filter Decisions

Enable debug logging to see filter activity:
```bash
./mvnw clean compile -X
```

Look for log messages from:
- `StrictRemoteRepositoryFilterSource` - Initialization and configuration
- `StrictRemoteRepositoryFilter` - Individual filter decisions
- `StrictFilterConfiguration` - Configuration loading

## CI/CD

The project uses GitHub Actions (`.github/workflows/ci.yml`):

- **Integration tests:** Matrix build across Java versions and test cases
- **Maven commands:** Use wrapper (`./mvnw`) with batch mode and no transfer progress

## Important Notes

- **Java version:** Minimum Java 17 required
- **Maven version:** 3.9.0 to 3.99.99 enforced by maven-enforcer-plugin
- **No dynamic versions:** Banned by enforcer plugin
- **Sisu indexing:** Required for Maven extension discovery (sisu-maven-plugin)
- **Provided scope:** Most Maven/Resolver dependencies are provided (not bundled)
