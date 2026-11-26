package com.cobalt.app

/** A GitHub release that is newer than the installed Cobalt build. */
data class AppUpdate(
    val version: String,
    val title: String,
    val publishedAt: String,
    val downloadUrl: String,
    val releaseUrl: String,
    val mainChanges: List<String>,
    val details: List<String>,
)

data class ChangeGroup(
    val title: String,
    val items: List<String>,
)

data class TranslationChangeLog(
    val date: String,
    val path: String,
    val mainGroups: List<ChangeGroup>,
    val details: List<String>,
)

sealed interface AppUpdateState {
    data object Idle : AppUpdateState
    data object Checking : AppUpdateState
    data class Available(val update: AppUpdate) : AppUpdateState
    data class Current(val version: String, val latest: AppUpdate? = null) : AppUpdateState
    data class Failed(val message: String) : AppUpdateState
}
