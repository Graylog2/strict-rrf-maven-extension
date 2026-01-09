# Strict Remote Repository Filter Maven Extension

[![CI](https://github.com/graylog-labs/strict-rrf-maven-extension/actions/workflows/ci.yml/badge.svg)](https://github.com/graylog-labs/strict-rrf-maven-extension/actions/workflows/ci.yml)

A Maven extension that implements the Maven Resolver Remote Repository Filter SPI. This extension provides properties-based configuration for filtering artifacts and metadata from remote Maven repositories.

## Features

- **Fail-secure Design**: Blocks all artifacts when no configuration exists
- **Flexible Pattern Matching**: Support for groupId and coordinate patterns with wildcards
- **Properties-based Configuration**: Use text files to configure filtering rules per repository

## How It Works

The filter uses a single `strict.properties` configuration file to define allow and deny rules for multiple repositories:

- **Default Deny**: Everything is denied by default unless explicitly allowed
- **Allow Rules**: Define which groupIds and artifacts are permitted from a repository
- **Deny Rules**: Override allow rules to block specific patterns
- **Glob Patterns**: Support wildcards (`*`) for flexible matching
- **Fail-secure**: If no configuration exists for a repository, all artifacts are blocked
- **Enabled by Default**: The filter activates automatically when the extension is registered

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

### Filter Behavior

The filter is **enabled by default** when the extension is registered in `.mvn/extensions.xml`.

To **disable** the filter:

```bash
mvn clean install -Daether.remoteRepositoryFilter.strict.enabled=false
```

Or configure in `.mvn/maven.config`:

```
-Daether.remoteRepositoryFilter.strict.enabled=false
```

### Configuration Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `aether.remoteRepositoryFilter.strict.enabled` | boolean | **true** | Enable/disable the filter globally |
| `aether.remoteRepositoryFilter.strict.basedir` | string | `.remoteRepositoryFilters` | Base directory for config files (relative to local repository: `~/.m2/repository/.remoteRepositoryFilters`) |

### Configuration File

A single configuration file named `strict.properties` should be placed in the filter basedir.

**Default location**: `~/.m2/repository/.remoteRepositoryFilters/strict.properties` (global configuration in local repository)

**Project-specific location (recommended)**: To use project-specific configuration in Maven 3.9+, add this to `.mvn/maven.config`:
```
-Daether.remoteRepositoryFilter.strict.basedir=${session.rootDirectory}/.mvn/remoteRepositoryFilters
```
Then create `.mvn/remoteRepositoryFilters/strict.properties` in your project root.

**Important Note for Maven 3.9.x**: The `${session.rootDirectory}` variable is only available for interpolation in `.mvn/maven.config` and command-line arguments, not as a runtime property. Maven 4.0+ will support it as a full property.

### File Format

The properties file defines allow and deny rules per repository:

```properties
# Shibboleth repository - allow and deny rules
repo.shibboleth.allow = org.opensaml*,net.shibboleth*
repo.shibboleth.deny = org.opensaml.internal*,net.shibboleth.internal*

# Maven Central - groupId patterns with wildcards
repo.central.allow = org.graylog*,org.apache.maven*,org.springframework*

# Company repository - coordinate patterns (groupId:artifactId)
repo.company.allow = com.company:*,com.other:lib-*

# Mixed patterns - both groupId and coordinate patterns
repo.test.allow = org.junit*,com.google:guava,com.google:gson

# Lines starting with # are comments
# Empty lines are ignored
```

**Format Rules**:
- **Allow rule**: `repo.{repositoryId}.allow = pattern1,pattern2,...`
  - GroupId exact match: `org.graylog` (matches only exact groupId `org.graylog`)
  - GroupId wildcard: `org.graylog*` (matches `org.graylog` and all sub-packages like `org.graylog.plugin`)
  - Coordinate pattern: `com.company:*` (matches all artifacts in groupId `com.company`)
  - Coordinate pattern: `com.test:lib-*` (matches artifacts starting with "lib-" in groupId `com.test`)
  - Coordinate exact match: `com.google:guava` (matches only the specific artifact)
- **Deny rule**: `repo.{repositoryId}.deny = pattern1,pattern2,...`
  - Same pattern formats as allow rules
- Whitespace around keys, values, and commas is automatically trimmed
- Comments start with `#`
- Empty lines are ignored

### Filtering Logic

The filter works in this order:

1. **Default Deny**: If no `.allow` patterns are specified, everything is denied
2. **Check Allow**: If artifact matches any `.allow` pattern, proceed to step 3; otherwise deny
3. **Check Deny**: If artifact matches any `.deny` pattern, deny; otherwise allow

**Pattern Matching**:

Patterns support both groupId-only and full coordinate (groupId:artifactId) patterns:

#### GroupId-Only Patterns

- **Exact match** (no wildcard):
  - `org.graylog` matches only: `org.graylog:*` (exact groupId match)
  - Does NOT match: `org.graylog.plugin:*`, `org.graylog2:*`

- **Wildcard matching**:
  - `org.graylog*` matches: `org.graylog:*`, `org.graylog.plugin:*`, `org.graylog.server.*:*`
  - `com.google.*` matches: `com.google.foo:*`, `com.google.bar.baz:*`
  - Does NOT match: `com.google:*` (requires dot after google)
  - `com.google*` matches: `com.google:*`, `com.googlecode:*`, `com.google.foo:*`
  - `*` matches: everything

#### Coordinate Patterns (groupId:artifactId)

- **Wildcard artifactId**:
  - `com.opensaml:*` matches: any artifact in groupId `com.opensaml`
  - Example: `com.opensaml:opensaml-core`, `com.opensaml:opensaml-saml`

- **Pattern artifactId**:
  - `com.foobar:test-*` matches: artifacts starting with "test-" in groupId `com.foobar`
  - Example: `com.foobar:test-utils`, `com.foobar:test-core`
  - Does NOT match: `com.foobar:production-lib`

- **Exact artifactId**:
  - `com.google:guava` matches: only `com.google:guava`
  - Does NOT match: `com.google:gson` or other artifacts from `com.google`

- **Mixed patterns**:
  - You can mix groupId-only and coordinate patterns in the same rule:
  - `repo.test.allow = org.graylog,com.opensaml:*,com.test:lib-*`
  - This allows: all `org.graylog.*` artifacts, all `com.opensaml` artifacts, and `com.test:lib-*` artifacts

## Usage Examples

### Example 1: Restrict Maven Central (Global Configuration)

Create `~/.m2/repository/.remoteRepositoryFilters/strict.properties`:

```properties
# Only allow these groupIds from Maven Central (with wildcards to include sub-packages)
repo.central.allow = org.graylog*,org.apache.maven*,org.apache.commons*
```

Run Maven (filter is enabled by default):

```bash
mvn clean compile
```

Only artifacts from the allowed groupIds and their sub-packages will be fetched from Maven Central. Everything else is denied by default.

**For project-specific configuration**, add to `.mvn/maven.config`:
```
-Daether.remoteRepositoryFilter.strict.basedir=${session.rootDirectory}/.mvn/remoteRepositoryFilters
```

Then create `.mvn/remoteRepositoryFilters/strict.properties` in your project root with the same content.

### Example 2: Allow with Deny Overrides

Allow broad groupIds but deny specific sub-packages:

```properties
# Shibboleth repository - allow main packages (with wildcards)
repo.shibboleth.allow = org.opensaml*,net.shibboleth*

# But deny internal packages
repo.shibboleth.deny = org.opensaml.internal*,net.shibboleth.internal*
```

This allows `org.opensaml:opensaml-core` and `org.opensaml.core:*` but denies `org.opensaml.internal:something`.

### Example 3: Multiple Repositories

Configure multiple repositories in a single file:

```properties
# Shibboleth repository
repo.shibboleth.allow = org.opensaml*,net.shibboleth*
repo.shibboleth.deny = *.internal*

# Maven Central
repo.central.allow = org.graylog*,org.apache.maven*

# Company internal repository
repo.company-nexus.allow = com.company*,com.company.internal*
```

### Example 4: Glob Pattern Wildcards

Use wildcards for flexible matching:

```properties
# Allow all Google libraries
repo.central.allow = com.google*

# But deny test utilities
repo.central.deny = com.google.*.test*

# Allow anything from company, deny snapshots
repo.company.allow = com.mycompany*
repo.company.deny = *-SNAPSHOT
```

### Example 5: Coordinate-Based Filtering

Restrict specific artifacts using full coordinates:

```properties
# Allow only specific Google artifacts (exact match)
repo.central.allow = com.google:guava,com.google:gson

# Allow all OpenSAML artifacts
repo.shibboleth.allow = com.opensaml:*,net.shibboleth:*

# Allow test utilities only
repo.test-repo.allow = com.company:test-*,org.junit:*

# Allow all from groupId but deny specific artifacts
repo.central.allow = org.apache.commons:*
repo.central.deny = org.apache.commons:commons-io

# Mix groupId and coordinate patterns
repo.central.allow = org.graylog*,com.opensaml:*,com.google:guava
```

### Example 6: Custom Config Directory

Use a custom config directory (different from the default `.mvn/remoteRepositoryFilters`):

```bash
mvn clean install \
  -Daether.remoteRepositoryFilter.strict.basedir=${session.rootDirectory}/.mvn/custom-config
```

Or add to `.mvn/maven.config`:
```
-Daether.remoteRepositoryFilter.strict.basedir=${session.rootDirectory}/.mvn/custom-config
```

Create `strict.properties` in `.mvn/custom-config/` in your project:

```
.mvn/
  custom-config/
    strict.properties
```

### Example 7: Temporarily Disable Filter

```bash
mvn clean install \
  -Daether.remoteRepositoryFilter.strict.enabled=false
```

This disables the filter entirely for this build.

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
- Artifact filtering with exact and wildcard matching
- Metadata filtering
- Relative and absolute path resolution
- Multiple repository configurations

## Debugging

Enable Maven debug output to see filter activity:

```bash
mvn clean compile -X
```

Look for log messages from `StrictRemoteRepositoryFilterSource` and `StrictRemoteRepositoryFilter`.

## Requirements

- **Maven**: 3.9.0 or later
- **Java**: 17 or later

## Important Notes

1. **The filter is enabled by default** - when you register the extension in `.mvn/extensions.xml`, it activates automatically
2. **Fail-secure behavior** - blocks all artifacts when no configuration exists
3. **Not for security** - use `maven-enforcer-plugin` for dependency policies
4. **Optimization tool** - designed to reduce unnecessary 404 requests and improve privacy

## References

- [Maven Resolver Remote Repository Filtering](https://maven.apache.org/resolver/remote-repository-filtering.html)
- [Maven Extension Guide](https://maven.apache.org/guides/mini/guide-using-extensions.html)
- [Maven Resolver SPI](https://maven.apache.org/resolver/maven-resolver-spi/)

---

*This project was developed with [Claude Code](https://claude.com/claude-code).*
