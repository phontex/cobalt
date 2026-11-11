package ru.phontex.cobalt.profile

/** Identity reported by Android for the installed target application. */
data class InstalledTarget(
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
)

data class ProfileIndexEntry(
    val id: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long?,
    val path: String,
)

/**
 * Selects only an exact package profile. A profile with a declared versionCode must match both
 * versionName and versionCode. A null versionCode is an incomplete legacy entry and is accepted
 * only by versionName so it cannot be mistaken for a different package.
 */
object ProfileResolver {
    fun find(entries: Iterable<ProfileIndexEntry>, target: InstalledTarget): ProfileIndexEntry? =
        entries.firstOrNull { entry ->
            entry.packageName == target.packageName &&
                entry.versionName == target.versionName &&
                (entry.versionCode == null || entry.versionCode == target.versionCode)
        }
}
