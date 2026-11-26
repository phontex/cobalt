# Version policy

Cobalt changes more than strings: translation today, interface adaptation and other app
modifications later. The version rules below apply to every kind of change.

## Principles

1. **The version is advisory, never a gate.** An installed build outside the verified range keeps
   working. Cobalt shows a warning on the application's own page and does nothing else.
2. **Each change is a separate unit.** A translation layer, a resource patch or a future
   modification declares its own verified versions and its own range. One unit being out of range
   must not disable another.
3. **A unit must fail safe, not fail loud.** If a unit cannot apply to an unknown build (a string
   is missing, a hook target is gone), it skips that item and records it in the log. It never
   crashes the target application.
4. **Statuses**
   - *verified* – the exact version is listed in the catalogue;
   - *inside range, not verified* – between `min_version` and `max_version`;
   - *older / newer than the range* – warning with the side of the range;
   - *unknown* – no catalogue data (offline, first start); neutral note.
5. **Profile selection** stays `package name -> versionName -> versionCode`. With no exact match
   the nearest version of the same package is used.
6. **Wording.** Every warning ends with the statement that Cobalt keeps working. Warnings live in
   the application's settings page, not in global dialogs.

## Where it is implemented

- `Compatibility.versionNotice` (`CatalogModels.kt`) – the single source of the status text.
- `CobaltProfiles.resolve` (`ProfileCatalog.kt`) – nearest-version fallback.
- Application page (`ProfileScreen`) and updates screen show the same notice.
- Runtime hook (`Hook.java`) and Platform `mount` do not depend on the version.
