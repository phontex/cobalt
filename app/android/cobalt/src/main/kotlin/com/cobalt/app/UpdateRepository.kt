package com.cobalt.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** Small, read-only GitHub client for app releases and translation changelogs. */
object UpdateRepository {
    private const val API = "https://api.github.com/repos/phontex/cobalt"
    private const val RAW = "https://raw.githubusercontent.com/phontex/cobalt/main/"
    private const val TIMEOUT = 20_000

    suspend fun checkApp(context: Context): Result<AppUpdateState> = withContext(Dispatchers.IO) {
        runCatching {
            val current = context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
            val releases = JSONArray(downloadText("$API/releases?per_page=20"))
            val candidates = buildList {
                for (index in 0 until releases.length()) {
                    val release = releases.optJSONObject(index) ?: continue
                    if (release.optBoolean("draft", false)) continue
                    val asset = release.optJSONArray("assets")?.let { assets ->
                        (0 until assets.length()).asSequence()
                            .mapNotNull { assets.optJSONObject(it) }
                            .firstOrNull { it.optString("name").endsWith(".apk") }
                    } ?: continue
                    val version = release.optString("tag_name").removePrefix("app-v")
                    add(AppUpdate(
                        version = version,
                        title = release.optString("name", version),
                        publishedAt = release.optString("published_at").orEmpty(),
                        downloadUrl = asset.optString("browser_download_url"),
                        releaseUrl = release.optString("html_url"),
                        mainChanges = releaseNotes(release.optString("body")).first,
                        details = releaseNotes(release.optString("body")).second,
                    ))
                }
            }
            val latest = candidates.maxWithOrNull(
                Comparator { left, right -> compareVersions(left.version, right.version) },
            )
            if (latest != null && compareVersions(latest.version, current) > 0) {
                AppUpdateState.Available(latest)
            } else {
                AppUpdateState.Current(current, latest)
            }
        }
    }

    suspend fun fetchTranslationChanges(
        catalog: Catalog,
        packageName: String? = null,
        layerName: String? = null,
    ): Result<TranslationChangeLog?> = withContext(Dispatchers.IO) {
        runCatching {
            val path = catalog.latestChangelogPath
            if (path.isBlank()) return@runCatching null
            val index = JSONObject(downloadText(RAW + catalog.changelogPath))
            val latest = index.optJSONArray("entries")?.optJSONObject(0) ?: return@runCatching null
            val markdown = downloadText(RAW + latest.optString("path"))
            parseMarkdownChange(latest.optString("date"), latest.optString("path"), markdown, packageName, layerName)
        }
    }

    suspend fun downloadApp(context: Context, update: AppUpdate): Result<Intent> = withContext(Dispatchers.IO) {
        runCatching {
            val directory = File(context.filesDir, "updates").apply { mkdirs() }
            val target = File(directory, "Cobalt-${update.version}.apk")
            download(update.downloadUrl, target)
            val authority = "${context.packageName}.updates"
            val uri = FileProvider.getUriForFile(context, authority, target)
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    private fun parseMarkdownChange(
        date: String,
        path: String,
        markdown: String,
        packageName: String? = null,
        layerName: String? = null,
    ): TranslationChangeLog {
        val groups = mutableListOf<ChangeGroup>()
        val details = mutableListOf<String>()
        var sectionTitle: String? = null
        var include = packageName == null && layerName == null

        markdown.lineSequence().forEach { line ->
            if (line.startsWith("## ")) {
                val heading = line.removePrefix("## ").trim().replace("`", "")
                val packageMatches = packageName == null || heading.startsWith("$packageName ·")
                val layerMatches = layerName == null || heading.contains("· $layerName ·")
                include = packageMatches && layerMatches
                sectionTitle = if (include) friendlySectionTitle(heading, layerName) else null
                return@forEach
            }
            if (!include) return@forEach
            if (!line.startsWith("### ")) return@forEach
            val summary = friendlyChangeCount(line.removePrefix("### ").trim()) ?: return@forEach
            val title = sectionTitle ?: "Изменения перевода"
            groups += ChangeGroup(title, listOf(summary))
        }
        return TranslationChangeLog(date, path, groups, details)
    }

    private fun friendlySectionTitle(heading: String, layerName: String?): String {
        if (layerName == "platform" || heading.contains("· platform ·")) return "Ресурсы приложения"
        return when {
            heading.contains("server.json", ignoreCase = true) -> "Онлайн-контент"
            heading.contains("dex.json", ignoreCase = true) -> "Строки приложения"
            heading.contains("assets.json", ignoreCase = true) -> "Дополнительные тексты"
            heading.contains("legacy.json", ignoreCase = true) -> "Перевод интерфейса"
            else -> "Перевод приложения"
        }
    }

    /** Render only a human-readable count; never show filenames, package IDs, source keys or placeholders. */
    private fun friendlyChangeCount(raw: String): String? {
        val match = Regex("(Добавлено|Изменено|Удалено)\\s*\\((\\d+)\\)", RegexOption.IGNORE_CASE).find(raw)
            ?: return null
        val verb = match.groupValues[1].lowercase(Locale.ROOT)
        val count = match.groupValues[2].toIntOrNull() ?: return null
        val label = when (verb) {
            "добавлено" -> "Добавлено переводов"
            "изменено" -> "Исправлено переводов"
            "удалено" -> "Удалено переводов"
            else -> return null
        }
        return "$label: $count"
    }

    private fun releaseNotes(body: String): Pair<List<String>, List<String>> {
        val bullets = body.lineSequence().filter { it.trim().startsWith("-") }
            .map { it.trim().removePrefix("-").trim() }.filter { it.isNotBlank() }.toList()
        return bullets.take(5) to bullets.drop(5).take(20)
    }

    private fun downloadText(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT; readTimeout = TIMEOUT; instanceFollowRedirects = true
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "Cobalt-Update-Checker")
        }
        return try {
            if (connection.responseCode !in 200..299) throw IllegalStateException("Сервер вернул ${connection.responseCode}")
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally { connection.disconnect() }
    }

    private fun download(url: String, target: File) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT; readTimeout = TIMEOUT; instanceFollowRedirects = true
        }
        try {
            if (connection.responseCode !in 200..299) throw IllegalStateException("Сервер вернул ${connection.responseCode}")
            val part = File(target.parentFile, target.name + ".part")
            connection.inputStream.use { input -> part.outputStream().use { output -> input.copyTo(output) } }
            if (!part.renameTo(target)) { target.delete(); check(part.renameTo(target)) }
        } finally { connection.disconnect() }
    }

    private data class VersionParts(val numbers: List<Int>, val beta: Int?)

    private fun versionParts(version: String): VersionParts {
        val base = version.removePrefix("v").substringBefore('-')
        val numbers = base.split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val beta = Regex("(?i)beta(?:[.-]?(\\d+))?").find(version)?.groupValues?.getOrNull(1)
            ?.toIntOrNull() ?: if (version.contains("beta", true)) 0 else null
        return VersionParts(numbers, beta)
    }

    private fun compareVersions(left: String, right: String): Int {
        val a = versionParts(left); val b = versionParts(right)
        for (i in 0 until maxOf(a.numbers.size, b.numbers.size)) {
            val delta = a.numbers.getOrElse(i) { 0 } - b.numbers.getOrElse(i) { 0 }
            if (delta != 0) return delta
        }
        return when {
            a.beta == null && b.beta == null -> 0
            a.beta == null -> 1
            b.beta == null -> -1
            else -> a.beta - b.beta
        }
    }

}
