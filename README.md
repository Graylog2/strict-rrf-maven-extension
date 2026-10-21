# Strict Remote Repository Filter Maven Extension

[![CI](https://github.com/Graylog2/strict-rrf-maven-extension/actions/workflows/ci.yml/badge.svg)](https://github.com/Graylog2/strict-rrf-maven-extension/actions/workflows/ci.yml)

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

Create `.mvn/extensions.xml` in your project root:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<extensions>
    <extension>
        <groupId>org.graylog.maven</groupId>
        <artifactId>strict-rrf-maven-extension</artifactId>
        <version>0.3.0</version>
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

**Project-specific location (recommended)**: Place `strict.properties` in your project's
`.mvn/remoteRepositoryFilters/` directory. It is discovered automatically — no `.mvn/maven.config`
or `basedir` property is required.

**Lookup order** (when `basedir` is not set, first match wins):

1. `<projectRoot>/.mvn/remoteRepositoryFilters/strict.properties` — project-local config
   (`<projectRoot>` is the directory containing the topmost `.mvn`). A present file is authoritative
   even when empty (fail-secure).
2. `~/.m2/repository/.remoteRepositoryFilters/strict.properties` — global config in the local repository.

Setting `-Daether.remoteRepositoryFilter.strict.basedir=<dir>` explicitly bypasses this lookup and
uses `<dir>` as the single configuration location.

### File Format

The properties file defines allow and deny rules per repository:

```properties
# Shibboleth repository - allow and deny rules
repo.shibboleth.allow = org.opensaml,org.opensaml.*,net.shibboleth,net.shibboleth.*
repo.shibboleth.deny = org.opensaml.internal,org.opensaml.internal.*

# Maven Central - exact groupIds plus their sub-groups (dot-anchored wildcards)
repo.central.allow = org.graylog,org.graylog.*,org.apache.maven,org.apache.maven.*

# Company repository - coordinate patterns (groupId:artifactId)
repo.company.allow = com.company:*,com.other:lib-*

# Mixed patterns - both groupId and coordinate patterns
repo.test.allow = org.junit,org.junit.*,com.google:guava,com.google:gson

# Lines starting with # are comments
# Empty lines are ignored
```

**Format Rules**:
- **Allow rule**: `repo.{repositoryId}.allow = pattern1,pattern2,...`
  - GroupId exact match: `org.graylog` (matches only exact groupId `org.graylog`)
  - GroupId sub-groups: `org.graylog.*` (matches sub-groups like `org.graylog.plugin`, but not `org.graylog` itself)
  - GroupId and sub-groups: `org.graylog,org.graylog.*` (recommended over `org.graylog*`, see the caution below)
  - Coordinate pattern: `com.company:*` (matches all artifacts in groupId `com.company`)
  - Coordinate pattern: `com.test:lib-*` (matches artifacts starting with "lib-" in groupId `com.test`)
  - Coordinate exact match: `com.google:guava` (matches only the specific artifact)
- **Deny rule**: `repo.{repositoryId}.deny = pattern1,pattern2,...`
  - Same pattern formats as allow rules
- Whitespace around keys, values, and commas is automatically trimmed
- Comments start with `#`
- Empty lines are ignored
- Each key may appear **only once**. List all patterns for a rule on one line, separated by commas
- Patterns may only contain letters, digits, `.`, `_`, `-` and `*`, with at most one `:` between
  groupId and artifactId. Versions (`g:a:1.0`), `?`, inner whitespace and `%regex[...]` are invalid

**Invalid configuration blocks the repository**: if a key appears more than once, or a pattern is
invalid, the filter logs a warning and blocks **all** artifacts from that repository until the file is
fixed. A rule is never silently ignored. A blank value (`repo.central.deny =`) is skipped with a
warning: a blank `.deny` blocks nothing, a blank `.allow` denies everything.

### Repository IDs and Mirrors

The `{repositoryId}` in each rule must be the ID of the repository that Maven **actually
downloads from** — which is *not* always the ID declared in your POM or in Maven's default
repositories.

When a mirror is configured in `settings.xml`, Maven replaces the mirrored repository with the
mirror before any download. The filter therefore sees the **mirror's ID**, not the original one.
Given this mirror:

```xml
<mirror>
  <id>nexus</id>
  <mirrorOf>*</mirrorOf>
  <url>https://nexus.internal/repository/maven-public</url>
</mirror>
```

rules must be keyed to `nexus`, not `central`:

```properties
# Correct: the resolver fetches everything through the "nexus" mirror
repo.nexus.allow = org.graylog,org.graylog.*,org.apache.maven,org.apache.maven.*

# Has no effect when the above mirror is active — "central" is never contacted directly
repo.central.allow = org.graylog,org.graylog.*
```

This is intentional and fail-secure: a mirror is a distinct download source and must be allowed
explicitly, just like any other repository. If no rule matches the effective repository ID, **all
artifacts from it are blocked**. A rule written for the wrong ID does not loosen the filter — it
simply never matches, so the build fails to resolve dependencies rather than fetching them
insecurely.

**Finding the effective repository ID**: run a build once with the filter disabled
(`-Daether.remoteRepositoryFilter.strict.enabled=false`) and check which ID artifacts are stored
under in `~/.m2/repository` (see the `_remote.repositories` marker files), or enable debug logging
(see [Debugging](#debugging)) and look for the `from repository: <id>` messages.

### Filtering Logic

The filter works in this order:

1. **Default Deny**: If no `.allow` patterns are specified, everything is denied
2. **Check Allow**: If artifact matches any `.allow` pattern, proceed to step 3; otherwise deny
3. **Check Deny**: If artifact matches any `.deny` pattern, deny; otherwise allow

> **Note:** Matching uses the `groupId` and `artifactId` only. The artifact **version is not considered**,
> so rules cannot allow or deny specific versions of an artifact (e.g. blocking a known-bad version while
> allowing others). A pattern like `*-SNAPSHOT` matches a *groupId* ending in `-SNAPSHOT` — which is not
> a real-world convention — and therefore effectively blocks nothing.

> **Note:** Deny patterns use the same matching as allow patterns, so a deny pattern without a wildcard
> is an **exact** match. `repo.central.deny = com.google.guava` does not block
> `com.google.guava.internal:*`. Use `com.google.guava,com.google.guava.*` to deny a group and its
> sub-groups. This matters most with a catch-all `allow = *`, where deny rules are the only restriction.

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

> **Caution — wildcards are greedy across dots:** `.` is not a separator, so `*` matches any characters
> at any depth. A bare-prefix pattern like `org.graylog*` also matches adjacent names such as
> `org.graylognasty` (a potential typosquat), and `com.*` allows the whole `com` namespace. In allow
> rules, prefer exact groupIds plus dot-anchored sub-groups: `org.graylog,org.graylog.*`.

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
  - This allows: the exact `org.graylog` groupId (not its sub-groups), all `com.opensaml` artifacts, and `com.test:lib-*` artifacts

## Usage Examples

### Example 1: Restrict Maven Central (Global Configuration)

Create `~/.m2/repository/.remoteRepositoryFilters/strict.properties`:

```properties
# Only allow these groupIds and their sub-groups from Maven Central
repo.central.allow = org.graylog,org.graylog.*,org.apache.maven,org.apache.maven.*,org.apache.commons
```

Run Maven (filter is enabled by default):

```bash
mvn clean compile
```

Only artifacts from the allowed groupIds (and, for `org.graylog` and `org.apache.maven`, their sub-groups) will be fetched from Maven Central. Everything else is denied by default.

**For project-specific configuration**, simply create `.mvn/remoteRepositoryFilters/strict.properties` in your project root with the same content. It is discovered automatically — no `.mvn/maven.config` or `basedir` property is required. (See the "Configuration File" section above for the full lookup order.)

### Example 2: Allow with Deny Overrides

Allow broad groupIds but deny specific sub-packages:

```properties
# Shibboleth repository - allow main groups and their sub-groups
repo.shibboleth.allow = org.opensaml,org.opensaml.*,net.shibboleth,net.shibboleth.*

# But deny internal groups and their sub-groups
repo.shibboleth.deny = org.opensaml.internal,org.opensaml.internal.*,net.shibboleth.internal,net.shibboleth.internal.*
```

This allows `org.opensaml:opensaml-core` and `org.opensaml.core:*` but denies `org.opensaml.internal:something`.

### Example 3: Multiple Repositories

Configure multiple repositories in a single file:

```properties
# Shibboleth repository
repo.shibboleth.allow = org.opensaml,org.opensaml.*,net.shibboleth,net.shibboleth.*
repo.shibboleth.deny = *.internal*

# Maven Central
repo.central.allow = org.graylog,org.graylog.*,org.apache.maven,org.apache.maven.*

# Company internal repository
repo.company-nexus.allow = com.company,com.company.*
```

### Example 4: Glob Pattern Wildcards

Use wildcards for flexible matching:

```properties
# Allow all Google libraries
repo.central.allow = com.google,com.google.*

# But deny test utilities
repo.central.deny = com.google.*.test*

# Allow anything from company, deny an internal sub-group
repo.company.allow = com.mycompany,com.mycompany.*
repo.company.deny = com.mycompany.internal,com.mycompany.internal.*
```

### Example 5: Coordinate-Based Filtering

Restrict specific artifacts using full coordinates. Each snippet below is a separate alternative —
a key may appear only once per file:

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
repo.central.allow = org.graylog,org.graylog.*,com.opensaml:*,com.google:guava
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

## Security Model

The filter restricts **which coordinates** (`groupId:artifactId`) may be fetched from **which
repository ID**. Keep its limits in mind:

- **No version or classifier matching**: once a `groupId:artifactId` is allowed, every version is allowed.
- **Repository IDs, not URLs**: rules are keyed by repository ID. The filter does not check which server
  the ID points to (see [Repository IDs and Mirrors](#repository-ids-and-mirrors)).
- **No integrity checks**: the filter does not verify checksums or signatures. Run Maven with
  `--strict-checksums` (`-C`) and use your usual signature/dependency policies alongside it.
- **The project controls its own configuration**: without an explicit `basedir`, a project-local
  `.mvn/remoteRepositoryFilters/strict.properties` takes precedence over the global configuration, and
  `.mvn/maven.config` can set `basedir` or disable the filter. Review changes to `.mvn/` like code.

Within those limits the filter fails secure: missing configuration, unknown repository IDs, unreadable
or oversized files, duplicate keys and invalid patterns all block artifacts rather than allow them.

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
3. **Complements dependency policies** - the filter limits which coordinates may be downloaded from which repository; it does not replace version or dependency policies such as `maven-enforcer-plugin` (see [Security Model](#security-model))
4. **Reduces requests and leaks** - artifacts are only requested from repositories allowed to serve them, avoiding unnecessary 404 requests and leaking coordinates to other repositories

## References

- [Maven Resolver Remote Repository Filtering](https://maven.apache.org/resolver/remote-repository-filtering.html)
- [Maven Extension Guide](https://maven.apache.org/guides/mini/guide-using-extensions.html)
- [Maven Resolver SPI](https://maven.apache.org/resolver/maven-resolver-spi/)

---

*This project was developed with [Claude Code](https://claude.com/claude-code).*
