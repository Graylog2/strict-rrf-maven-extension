# Strict Remote Repository Filter Maven Extension

A skeleton Maven extension that implements the Maven Resolver Remote Repository Filter SPI. This extension provides properties-based configuration for filtering artifacts and metadata from remote Maven repositories.

## Features

- **Properties-based Configuration**: Use text files to configure filtering rules per repository
- **Fail-safe Design**: Abstains from filtering when not enabled or misconfigured
- **Customizable**: Easy to extend with your own filtering logic
- **Well-tested**: Comprehensive unit tests included
- **Maven 3.9+ Compatible**: Uses Maven Resolver 2.x SPI

## How It Works

The filter uses configuration files to determine which artifacts should be fetched from specific repositories. By default, it implements groupId-based filtering with prefix matching:

- If a configuration file exists for a repository, only artifacts matching the configured groupIds are allowed
- If no configuration exists, all artifacts are allowed (fail-safe behavior)
- The filter must be explicitly enabled via system properties

## Installation

### 1. Build and Install

```bash
mvn clean install
```

This installs the extension to your local Maven repository (`~/.m2/repository`).

### 2. Register in Your Project

Create `.mvn/extensions.xml` in your project root:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<extensions>
    <extension>
        <groupId>org.graylog.maven</groupId>
        <artifactId>strict-rrf-maven-extension</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </extension>
</extensions>
```

## Configuration

### Enable the Filter

The filter must be explicitly enabled via system property:

```bash
mvn clean install -Daether.remoteRepositoryFilter.strict.enabled=true
```

Or configure in `.mvn/maven.config`:

```
-Daether.remoteRepositoryFilter.strict.enabled=true
```

### Configuration Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `aether.remoteRepositoryFilter.strict.enabled` | boolean | false | Enable/disable the filter |
| `aether.remoteRepositoryFilter.strict.basedir` | string | `.remoteRepositoryFilters/strict` | Base directory for config files (relative to local repo) |
| `aether.remoteRepositoryFilter.strict.{repoId}` | boolean | true | Enable/disable for specific repository |

### Configuration Files

Configuration files should be placed in the filter basedir and named: `strict-{repositoryId}.txt`

**Default location**: `~/.m2/repository/.remoteRepositoryFilters/strict/`

**Example**: `strict-central.txt` (for Maven Central)

### File Format

Each configuration file contains one groupId per line:

```properties
# Allow only these groupIds from this repository
org.graylog
org.graylog2
org.apache.commons
org.springframework

# Lines starting with # are comments
# Empty lines are ignored
```

### Filtering Logic

The default implementation uses **prefix matching**:

- `org.graylog` matches:
  - `org.graylog` (exact)
  - `org.graylog.plugin` (prefix)
  - `org.graylog.server.something` (prefix)

- `org.graylog` does NOT match:
  - `org.graylog2` (not a prefix match)
  - `com.example` (different groupId)

## Usage Examples

### Example 1: Restrict Maven Central

Create `~/.m2/repository/.remoteRepositoryFilters/strict/strict-central.txt`:

```
org.graylog
org.apache.maven
org.apache.commons
```

Run Maven with filter enabled:

```bash
mvn clean compile -Daether.remoteRepositoryFilter.strict.enabled=true
```

Only artifacts from the allowed groupIds will be fetched from Maven Central.

### Example 2: Custom Config Directory

Use a project-specific config directory:

```bash
mvn clean install \
  -Daether.remoteRepositoryFilter.strict.enabled=true \
  -Daether.remoteRepositoryFilter.strict.basedir=${session.rootDirectory}/.mvn/rrf
```

Create config files in `.mvn/rrf/` in your project:

```
.mvn/
  rrf/
    strict-central.txt
    strict-company-repo.txt
```

### Example 3: Disable for Specific Repository

```bash
mvn clean install \
  -Daether.remoteRepositoryFilter.strict.enabled=true \
  -Daether.remoteRepositoryFilter.strict.central=false
```

This enables the filter globally but disables it for Maven Central.

## Customization

The skeleton provides two main customization points in `StrictFilterConfiguration.java`:

### 1. Artifact Filtering Logic

Modify `isArtifactAllowed()` to implement custom filtering:

```java
public boolean isArtifactAllowed(String repositoryId, Artifact artifact) {
    // Your custom logic here
    // Examples:
    // - Exact groupId + artifactId matching
    // - Version range restrictions
    // - Regular expression patterns
    // - Classifier-based filtering
}
```

### 2. Configuration Format

Modify `loadRepositoryConfig()` to support different formats:

```java
private static void loadRepositoryConfig(Path file, Map<String, Set<String>> allowedGroupIds) {
    // Your custom parser here
    // Examples:
    // - JSON configuration
    // - YAML configuration
    // - Properties files with key-value pairs
    // - XML configuration
}
```

## Architecture

The extension consists of 4 main classes:

1. **StrictRemoteRepositoryFilterSource** - SPI entry point with `@Named("strict")` annotation
2. **StrictRemoteRepositoryFilter** - Implements the filtering logic
3. **StrictFilterConfiguration** - Loads and provides configuration
4. **SimpleFilterResult** - Result object for filter decisions

Maven discovers the extension via Sisu/JSR-330 dependency injection. The `@Named("strict")` annotation makes "strict" the filter identifier used in system properties.

## Testing

Run unit tests:

```bash
mvn test
```

The test suite includes:
- Configuration loading from files
- Empty/missing configuration handling
- Artifact filtering with prefix matching
- Metadata filtering
- Relative and absolute path resolution
- Multiple repository configurations

## Debugging

Enable Maven debug output to see filter activity:

```bash
mvn clean compile -X -Daether.remoteRepositoryFilter.strict.enabled=true
```

Look for log messages from `StrictRemoteRepositoryFilterSource` and `StrictRemoteRepositoryFilter`.

## Requirements

- **Maven**: 3.9.0 or later
- **Java**: 11 or later
- **Maven Resolver**: 2.0.14 (provided by Maven 3.9+)

## Important Notes

1. **The filter must be explicitly enabled** - it's disabled by default for safety
2. **Fail-safe behavior** - misconfiguration won't break builds
3. **Not for security** - use `maven-enforcer-plugin` for dependency policies
4. **Optimization tool** - designed to reduce unnecessary 404 requests and improve privacy

## License

This is a skeleton/template implementation. Customize it for your needs.

## References

- [Maven Resolver Remote Repository Filtering](https://maven.apache.org/resolver/remote-repository-filtering.html)
- [Maven Extension Guide](https://maven.apache.org/guides/mini/guide-using-extensions.html)
- [Maven Resolver SPI](https://maven.apache.org/resolver/maven-resolver-spi/)
