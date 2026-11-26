package com.cobalt.app

import org.json.JSONObject
import java.security.MessageDigest

/**
 * Who is responsible for a layer of a translation.
 *
 * The split is not cosmetic: it decides which component has to do the work and where the data
 * comes from. Runtime text is replaced inside the target process by the LSPosed part and is
 * served by Cobalt App. Text baked into the APK resources cannot be reached at runtime, so it is
 * delivered as a payload and applied by Cobalt Platform.
 */
enum class ServedBy { APP, PLATFORM }

/** One independently updatable translation layer of one application. */
data class TranslationLayer(
    val kind: String,
    val servedBy: ServedBy,
    val language: String,
    val languageName: String,
    val label: String,
    val contentId: String,
    val path: String,
    val sha256: String,
    val bytes: Long,
    val entries: Int,
    val scope: String,
    val verifiedVersion: String,
    val payloadEntry: String,
    val available: Boolean,
    val changelogPath: String,
    val changeSummary: String,
) {
    val isRuntime: Boolean get() = servedBy == ServedBy.APP
    val isPayload: Boolean get() = servedBy == ServedBy.PLATFORM
    val isUsable: Boolean get() = available && path.isNotBlank() && sha256.isNotBlank()

    fun describe(): String = buildList {
        add(languageName.ifBlank { language })
        if (label.isNotBlank()) add(label)
        if (entries > 0) add("$entries строк")
        if (bytes > 0) add(formatBytes(bytes))
    }.joinToString(" · ")

    private fun formatBytes(value: Long): String =
        if (value >= 1_048_576) "%.1f МБ".format(value / 1_048_576.0) else "${value / 1024} КБ"
}

/**
 * How the catalogue treats the installed application version.
 *
 * The range is advisory on purpose. A build outside [minVersion]..[maxVersion] still receives
 * the translation — the user is told that it is unverified and which side of the range it fell
 * on, because refusing would silently break a working translation after an app update.
 */
data class Compatibility(
    val policy: String,
    val verifiedVersions: List<String>,
    val note: String,
    val minVersion: String,
    val maxVersion: String,
) {
    fun isVerified(versionName: String): Boolean =
        versionName.isNotBlank() && verifiedVersions.any { it.equals(versionName, ignoreCase = true) }

    /** True when the catalogue published at least one bound. */
    val hasRange: Boolean get() = minVersion.isNotBlank() || maxVersion.isNotBlank()

    /** Null when the installed version is inside the published range. */
    fun rangeWarning(versionName: String): String? {
        if (!hasRange || versionName.isBlank()) return null
        if (minVersion.isNotBlank() && compareVersions(versionName, minVersion) < 0) {
            return "Установленная версия $versionName старше поддерживаемой ($minVersion). " +
                "Часть строк может остаться без перевода."
        }
        if (maxVersion.isNotBlank() && compareVersions(versionName, maxVersion) > 0) {
            return "Версия $versionName новее проверенной ($maxVersion). " +
                "Перевод ещё не сверен с этой сборкой: новые строки останутся на исходном языке."
        }
        return null
    }

    /**
     * Text for the application's own settings page. It never blocks anything: every branch ends
     * with the fact that Cobalt keeps working with the installed build.
     */
    fun versionNotice(versionName: String): VersionNotice = when {
        versionName.isBlank() -> VersionNotice(false, "Версию приложения определить не удалось. Cobalt продолжает работать.")
        isVerified(versionName) -> VersionNotice(false, "Версия $versionName проверена.")
        else -> rangeWarning(versionName)?.let { VersionNotice(true, "$it Cobalt продолжает работать.") }
            ?: VersionNotice(true, "Версия $versionName ещё не проверена. Cobalt продолжает работать; часть строк может остаться без перевода.")
    }
}

/** [warning] is true when the user should be told something; the text is ready to show. */
data class VersionNotice(val warning: Boolean, val text: String)

/** Numeric-aware comparison: 17.23.0beta1 > 17.22.1, and stable beats beta of the same build. */
internal fun compareVersions(left: String, right: String): Int {
    fun parts(value: String): Pair<List<Int>, Int?> {
        val base = value.removePrefix("v").substringBefore('-')
        val numbers = base.split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val beta = Regex("(?i)beta(?:[.-]?(\\d+))?").find(value)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: if (value.contains("beta", true)) 0 else null
        return numbers to beta
    }
    val (a, aBeta) = parts(left)
    val (b, bBeta) = parts(right)
    for (i in 0 until maxOf(a.size, b.size)) {
        val delta = a.getOrElse(i) { 0 } - b.getOrElse(i) { 0 }
        if (delta != 0) return delta
    }
    return when {
        aBeta == null && bBeta == null -> 0
        aBeta == null -> 1
        bBeta == null -> -1
        else -> aBeta.compareTo(bBeta)
    }
}

/** One application the catalogue knows about. */
data class CatalogApp(
    val packageName: String,
    val name: String,
    val purpose: String,
    val layers: List<TranslationLayer>,
    val compatibility: Compatibility,
) {
    val runtimeLayers: List<TranslationLayer> get() = layers.filter { it.isRuntime }
    val platformLayers: List<TranslationLayer> get() = layers.filter { it.isPayload }
    val bestRuntime: TranslationLayer? get() = runtimeLayers.firstOrNull { it.isUsable }
    val bestPayload: TranslationLayer? get() = platformLayers.firstOrNull { it.isUsable }
}

/**
 * Remote catalogue.
 *
 * Schema 2 separates translation layers by responsibility and treats the application version as
 * advisory: a version the catalogue has not verified is a warning, never a refusal. Schema 1
 * catalogues are still readable, their dictionaries become runtime layers.
 */
data class Catalog(
    val schemaVersion: Int,
    val updated: String,
    val minAppVersionCode: Int,
    val apps: List<CatalogApp>,
    val changelogPath: String,
    val latestChangelogPath: String,
) {
    fun app(packageName: String): CatalogApp? = apps.firstOrNull { it.packageName == packageName }

    companion object {
        const val URL = "https://raw.githubusercontent.com/phontex/cobalt/main/manifests/catalog.json"

        fun parse(root: JSONObject): Catalog {
            val apps = mutableListOf<CatalogApp>()
            val array = root.optJSONArray("apps") ?: return Catalog(1, "", 0, emptyList(), "", "")
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val packageName = item.optString("package").orEmpty()
                if (packageName.isBlank()) continue

                val layers = mutableListOf<TranslationLayer>()
                // Schema 2
                item.optJSONArray("layers")?.let { list ->
                    for (j in 0 until list.length()) {
                        list.optJSONObject(j)?.let { layers += parseLayer(it) }
                    }
                }
                // Schema 1 fallback
                item.optJSONArray("dictionaries")?.let { list ->
                    for (j in 0 until list.length()) {
                        val d = list.optJSONObject(j) ?: continue
                        layers += TranslationLayer(
                            kind = "runtime",
                            servedBy = ServedBy.APP,
                            language = d.optString("language", "ru"),
                            languageName = d.optString("language_name").orEmpty(),
                            label = d.optString("label").orEmpty(),
                            contentId = d.optString("content_id").orEmpty(),
                            path = d.optString("path").orEmpty(),
                            sha256 = d.optString("sha256").orEmpty(),
                            bytes = d.optLong("bytes", 0L),
                            entries = d.optInt("entries", 0),
                            scope = "runtime",
                            verifiedVersion = d.optString("version_name").orEmpty(),
                            payloadEntry = "",
                            available = d.optInt("entries", 0) > 0,
                            changelogPath = d.optString("changelog_path").orEmpty(),
                            changeSummary = d.optString("change_summary").orEmpty(),
                        )
                    }
                }

                val compatibility = item.optJSONObject("compatibility")?.let { c ->
                    val versions = mutableListOf<String>()
                    c.optJSONArray("verified_versions")?.let { v ->
                        for (k in 0 until v.length()) v.optString(k).takeIf { it.isNotBlank() }?.let(versions::add)
                    }
                    Compatibility(
                        policy = c.optString("policy", "warn"),
                        verifiedVersions = versions,
                        note = c.optString("note").orEmpty(),
                        minVersion = c.optString("min_version").orEmpty(),
                        maxVersion = c.optString("max_version").orEmpty(),
                    )
                } ?: Compatibility("warn", emptyList(), "", "", "")

                apps += CatalogApp(packageName, item.optString("name", packageName), item.optString("purpose").orEmpty(), layers, compatibility)
            }
            val changes = root.optJSONObject("changelog")
            return Catalog(
                root.optInt("schema_version", 1),
                root.optString("updated").orEmpty(),
                root.optInt("min_app_version_code", 0),
                apps,
                changes?.optString("path").orEmpty(),
                changes?.optString("latest").orEmpty(),
            )
        }

        private fun parseLayer(item: JSONObject): TranslationLayer {
            val payload = item.optJSONObject("payload")
            val servedBy = when (item.optString("served_by")) {
                "platform" -> ServedBy.PLATFORM
                else -> ServedBy.APP
            }
            return TranslationLayer(
                kind = item.optString("kind", "runtime"),
                servedBy = servedBy,
                language = item.optString("language", "ru"),
                languageName = item.optString("language_name").orEmpty(),
                label = item.optString("label").orEmpty(),
                contentId = item.optString("content_id").orEmpty(),
                path = payload?.optString("path")?.takeIf { it.isNotBlank() } ?: item.optString("path").orEmpty(),
                sha256 = payload?.optString("sha256")?.takeIf { it.isNotBlank() } ?: item.optString("sha256").orEmpty(),
                bytes = payload?.optLong("bytes", 0L) ?: item.optLong("bytes", 0L),
                entries = item.optInt("entries", 0),
                scope = item.optString("scope").orEmpty(),
                verifiedVersion = item.optString("verified_version").orEmpty(),
                payloadEntry = payload?.optString("entry")?.takeIf { it.isNotBlank() }
                    ?: item.optString("entry").orEmpty(),
                available = if (payload != null) payload.optBoolean("available", false)
                else item.optBoolean("available", item.optInt("entries", 0) > 0),
                changelogPath = item.optString("changelog_path").orEmpty(),
                changeSummary = item.optString("change_summary").orEmpty(),
            )
        }
    }
}

/** SHA-256 helper shared by the catalogue client. */
internal fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
