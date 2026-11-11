# Cobalt Platform

Minimal SukiSU/Magisk-compatible system component for Cobalt.

## Current scope

- Creates `/data/adb/cobalt-platform/{logs,inbox,cache,backup}`.
- Writes a status marker.
- Discovers installed package paths, version information and split APKs on explicit request.
- Keeps deterministic per-package profiles.
- Provides log/status/discovery commands.
- Does **not** download files, contain APKs, patch arbitrary APKs, or apply an unreviewed adapter.

Text translation remains in the Cobalt LSPosed runtime and Cobalt App dictionary. APK/resource patching will only be enabled after a package/version-specific adapter is reviewed.

## Commands

```sh
cobalt-platform status
cobalt-platform discover com.heytap.themestore
cobalt-platform apply com.heytap.themestore
cobalt-platform rollback com.heytap.themestore
cobalt-platform logs
cobalt-platform clear-logs
```
