package com.cobalt.app

import android.content.Context
import androidx.core.content.edit
import org.json.JSONObject

/**
 * Single source of truth for the user dictionary.
 *
 * Both the dictionary workspace and the editor read and write through this object, so the
 * size limits, the JSON format and the validation rules cannot drift apart. Storage stays
 * exactly where it was: SharedPreferences("local") → key "dictionary".
 */
object DictionaryStore {
    const val MAX_ENTRIES = 5000
    const val MAX_BYTES = 1_048_576
    const val MAX_FIELD = 4096

    private fun prefs(context: Context) = context.getSharedPreferences("local", 0)

    fun read(context: Context): JSONObject = try {
        JSONObject(prefs(context).getString("dictionary", "{}") ?: "{}")
    } catch (_: Exception) {
        JSONObject()
    }

    fun count(context: Context): Int = read(context).length()

    fun save(context: Context, data: JSONObject): Boolean {
        if (data.length() > MAX_ENTRIES) return false
        if (data.toString().toByteArray(Charsets.UTF_8).size > MAX_BYTES) return false
        prefs(context).edit(commit = true) { putString("dictionary", data.toString()) }
        return true
    }

    fun put(context: Context, original: String, translation: String): Boolean {
        if (original.isBlank() || translation.isBlank()) return false
        if (original.length > MAX_FIELD || translation.length > MAX_FIELD) return false
        val data = read(context)
        data.put(original, translation)
        return save(context, data)
    }

    fun remove(context: Context, original: String): Boolean {
        val data = read(context)
        data.remove(original)
        return save(context, data)
    }

    /** Entries sorted by source string, for a stable list. */
    fun entries(context: Context): List<Pair<String, String>> {
        val data = read(context)
        return data.keys().asSequence().toList().sorted().map { it to data.optString(it) }
    }

    fun export(context: Context): String =
        JSONObject().put("format", 1).put("translations", read(context)).toString(2)


    /**
     * Entries the Theme Store process should see: the downloaded translation for the version
     * that is installed, overlaid by the user's own records (a personal entry always wins).
     *
     * The provider serves this instead of the raw user dictionary; the user dictionary itself
     * is neither modified nor used as storage for downloaded translations.
     */
    fun served(context: Context, packageName: String?): JSONObject {
        val merged = JSONObject()
        if (packageName != null) {
            val server = DictionaryInstaller.entries(context, packageName)
            if (server != null) {
                val keys = server.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    if (key.startsWith("\u0001cobalt_")) continue
                    val value = server.optString(key)
                    if (key.isNotBlank() && value.isNotBlank()) merged.put(key, value)
                }
            }
        }
        val own = read(context)
        val keys = own.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (key.startsWith("\u0001cobalt_")) continue
            merged.put(key, own.optString(key))
        }
        return merged
    }

    /** Parses an import file and merges it over the current dictionary. */
    fun merge(context: Context, bytes: ByteArray): Result<Pair<JSONObject, Int>> = runCatching {
        if (bytes.size > MAX_BYTES) throw IllegalArgumentException("Файл больше 1 МБ")
        val root = JSONObject(String(bytes, Charsets.UTF_8))
        if (root.optInt("format") != 1) throw IllegalArgumentException("Неизвестный формат файла")
        val incoming = root.getJSONObject("translations")
        val merged = read(context)
        var count = 0
        val keys = incoming.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = incoming.get(key)
            if (value !is String || key.isBlank() || value.isBlank() ||
                key.length > MAX_FIELD || value.length > MAX_FIELD
            ) throw IllegalArgumentException("Некорректная запись в файле")
            merged.put(key, value)
            count++
        }
        merged to count
    }

    fun readAll(stream: java.io.InputStream): ByteArray = stream.use { input ->
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            out.write(buffer, 0, read)
            if (out.size() > MAX_BYTES) throw IllegalArgumentException("Файл больше 1 МБ")
        }
        out.toByteArray()
    }
}
