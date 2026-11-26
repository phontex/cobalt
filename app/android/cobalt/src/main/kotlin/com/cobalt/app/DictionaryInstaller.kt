package com.cobalt.app

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** What is currently installed on the device for one package. */
data class InstalledDictionary(
    val packageName: String,
    val language: String,
    val label: String,
    val contentId: String,
    val entries: Int,
    val installedAt: Long,
)

/**
 * Storage for translations downloaded from the catalog.
 *
 * Downloaded translations are kept **outside** the user dictionary: the user dictionary is
 * capped at 5 000 entries / 1 MiB and belongs to the person editing it, while a published
 * translation can be an order of magnitude larger and is replaced on update. Files live under
 * `files/server-dictionary/<package>/<language>.json`, metadata under the "server" preferences.
 */
object DictionaryInstaller {
    private const val PREFS = "server"
    private const val NETWORK_TIMEOUT = 20_000

    private fun root(context: Context) = File(context.filesDir, "server-dictionary")

    private fun file(context: Context, packageName: String, language: String) =
        File(File(root(context), packageName), "$language.json")

    private fun meta(context: Context) = context.getSharedPreferences(PREFS, 0)

    /** Entries of the translation currently installed for [packageName], or null. */
    fun entries(context: Context, packageName: String, language: String = "ru"): JSONObject? = runCatching {
        val f = file(context, packageName, language)
        if (!f.isFile) return null
        flattenedEntries(JSONObject(f.readText()))
    }.getOrNull()

    fun installed(context: Context, packageName: String, language: String = "ru"): InstalledDictionary? {
        val p = meta(context)
        val key = "$packageName:$language"
        val label = p.getString("label_$key", null) ?: return null
        val f = file(context, packageName, language)
        if (!f.isFile) return null
        return InstalledDictionary(
            packageName = packageName,
            language = language,
            label = label,
            contentId = p.getString("content_$key", "").orEmpty(),
            entries = p.getInt("entries_$key", 0),
            installedAt = p.getLong("time_$key", 0L),
        )
    }

    fun remove(context: Context, packageName: String, language: String = "ru") {
        val key = "$packageName:$language"
        file(context, packageName, language).delete()
        meta(context).edit {
            remove("label_$key"); remove("content_$key"); remove("entries_$key"); remove("time_$key")
        }
    }

    /** Accepts both the current generated entries view and older flat dictionaries. */
    private fun flattenedEntries(root: JSONObject): JSONObject {
        val result = JSONObject()
        val sections = root.optJSONObject("sections")
        if (sections != null) {
            val names = sections.keys().asSequence().toList().sortedWith(compareBy<String> {
                when (it) { "legacy" -> 0; "dex" -> 1; "assets" -> 2; "server" -> 3; else -> 4 }
            }.thenBy { it })
            for (name in names) {
                val section = sections.optJSONObject(name) ?: continue
                val keys = section.keys()
                while (keys.hasNext()) {
                    val key = keys.next(); val value = section.optString(key)
                    if (key.isNotBlank() && value.isNotBlank()) result.put(key, value)
                }
            }
        }
        val flat = root.optJSONObject("entries")
        if (flat != null) {
            val keys = flat.keys()
            while (keys.hasNext()) {
                val key = keys.next(); val value = flat.optString(key)
                if (key.isNotBlank() && value.isNotBlank()) result.put(key, value)
            }
        }
        return result
    }

    /**
     * Downloads [dictionary], verifies it and only then replaces the installed copy.
     *
     * The body is checked against the catalog checksum before it is written anywhere, so a
     * truncated download or a replaced file on the server can never become the active
     * translation. Nothing is written to the user layer.
     */
    suspend fun install(
        context: Context,
        packageName: String,
        layer: TranslationLayer,
        onProgress: (Float) -> Unit = {},
    ): Result<InstalledDictionary> = withContext(Dispatchers.IO) {
        runCatching {
            val url = if (layer.path.startsWith("http")) layer.path
            else "https://raw.githubusercontent.com/phontex/cobalt/main/" + layer.path
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = NETWORK_TIMEOUT
                readTimeout = NETWORK_TIMEOUT
                instanceFollowRedirects = true
                requestMethod = "GET"
            }
            val bytes = try {
                if (connection.responseCode !in 200..299) {
                    throw IllegalStateException("Сервер вернул ${connection.responseCode}")
                }
                val total = connection.contentLengthLong
                val out = java.io.ByteArrayOutputStream(if (total > 0) total.toInt() else 1 shl 16)
                connection.inputStream.use { input ->
                    val buffer = ByteArray(16 * 1024)
                    var read: Int
                    var done = 0L
                    while (input.read(buffer).also { read = it } > 0) {
                        out.write(buffer, 0, read)
                        done += read
                        if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
                out.toByteArray()
            } finally {
                connection.disconnect()
            }

            if (layer.sha256.isNotBlank()) {
                val actual = sha256Hex(bytes)
                if (!actual.equals(layer.sha256, ignoreCase = true)) {
                    throw IllegalStateException("Контрольная сумма не совпала")
                }
            }

            val parsed = JSONObject(String(bytes, Charsets.UTF_8))
            val entries = flattenedEntries(parsed)
            if (entries.length() == 0) throw IllegalStateException("В файле нет записей перевода")

            val target = file(context, packageName, layer.language)
            target.parentFile?.mkdirs()
            val temp = File(target.parentFile, target.name + ".part")
            temp.writeBytes(bytes)
            if (!temp.renameTo(target)) {
                target.delete()
                if (!temp.renameTo(target)) throw IllegalStateException("Не удалось сохранить файл")
            }

            val key = "$packageName:${layer.language}"
            meta(context).edit {
                putString("label_$key", layer.label.ifBlank { layer.contentId })
                putString("content_$key", layer.contentId)
                putInt("entries_$key", entries.length())
                putLong("time_$key", System.currentTimeMillis())
            }

            InstalledDictionary(
                packageName = packageName,
                language = layer.language,
                label = layer.label,
                contentId = layer.contentId,
                entries = entries.length(),
                installedAt = System.currentTimeMillis(),
            )
        }
    }
}
