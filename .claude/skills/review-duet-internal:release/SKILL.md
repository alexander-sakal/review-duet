---
name: review-duet-internal:release
description: Use when releasing a new plugin version - bumps version in gradle.properties and creates git tag
---

# Release Plugin

Bump plugin version and create git tag for release.

## Usage

```
/review-duet-internal:release [version]
```

- With version: `/review-duet-internal:release 0.6.0` - uses specified version
- Without version: auto-bumps minor (e.g., `0.5.0` → `0.6.0`)

## Process

1. **Read current version**
   ```bash
   grep "^pluginVersion" plugin/gradle.properties
   ```

2. **Determine new version**
   - If provided: use it
   - If not: bump minor version (X.Y.Z → X.Y+1.0)

3. **Update gradle.properties**
   ```bash
   # Edit pluginVersion line in plugin/gradle.properties
   ```

4. **Commit version bump**
   ```bash
   git add plugin/gradle.properties
   git commit -m "chore: bump plugin version to <version>"
   ```

5. **Create annotated tag**
   ```bash
   git tag -a v<version> -m "Release v<version>"
   ```

6. **Report** - show commands to push:
   ```
   git push origin main
   git push origin v<version>
   ```

## Version Format

Use semantic versioning: `MAJOR.MINOR.PATCH`
- Tag format: `v0.6.0`
