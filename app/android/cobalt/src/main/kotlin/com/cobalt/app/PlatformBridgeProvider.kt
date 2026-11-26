package com.cobalt.app

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import org.json.JSONArray
import org.json.JSONObject

/**
 * Read-only bridge that lets Cobalt Platform ask Cobalt App what to do.
 *
 * The platform cannot read the catalogue or the preferences, so it asks. Every request must
 * carry the token that was handed to the platform through `configure`; without it the provider
 * refuses, so an unrelated root process cannot read the data either.
 *
 * Only three things are ever served, all as data — never as instructions to execute:
 *   /supported              list of applications the catalogue knows
 *   /plan/<package>         what may be changed in this application, as data
 *   /state/<package>        installed translation and its version
 */
class PlatformBridgeProvider : ContentProvider() {
    companion object {
        const val AUTHORITY = "com.cobalt.app.platform"
        private const val COLUMN = "p"
        private const val BASE = "https://raw.githubusercontent.com/phontex/cobalt/main/"
    }

    private fun prefs(context: Context) = context.getSharedPreferences("local", 0)

    private fun expectedToken(context: Context): String? =
        prefs(context).getString("platform_token", null)?.takeIf { it.isNotBlank() }

    private fun check(uri: Uri) {
        val context = context ?: throw IllegalStateException("no context")
        if (uri.authority != AUTHORITY) throw SecurityException("Invalid Cobalt bridge URI")
        val token = uri.getQueryParameter("token")
        val expected = expectedToken(context)
        if (expected == null || token == null || token != expected) {
            throw SecurityException("Cobalt bridge token missing or wrong")
        }
    }

    private fun row(value: String): Cursor {
        val cursor = MatrixCursor(arrayOf(COLUMN))
        cursor.addRow(arrayOf<Any>(value))
        return cursor
    }

    private fun appJson(context: Context, app: CatalogApp): JSONObject {
        val identity = CobaltProfiles.installed(context, app.packageName)
        val installedDictionary = DictionaryInstaller.installed(context, app.packageName)
        return JSONObject()
            .put("package", app.packageName)
            .put("name", app.name)
            .put("installed", identity != null)
            .put("version_name", identity?.versionName ?: JSONObject.NULL)
            .put("version_code", identity?.versionCode ?: JSONObject.NULL)
            .put("verified", identity != null && app.compatibility.isVerified(identity.versionName))
            .put("policy", app.compatibility.policy)
            .put("translation", JSONObject().apply {
                put("label", installedDictionary?.label ?: "")
                put("content_id", installedDictionary?.contentId ?: "")
                put("entries", installedDictionary?.entries ?: 0)
            })
    }

    override fun onCreate(): Boolean = true

    override fun query(uri: Uri, projection: Array<String>?, selection: String?, args: Array<String>?, sort: String?): Cursor {
        check(uri)
        val context = context ?: throw IllegalStateException("Provider is not attached")
        val state = CatalogRepository.cached(context)
        val apps = state.catalog?.apps.orEmpty()
        return when (uri.pathSegments.firstOrNull()) {
            "supported" -> {
                val array = JSONArray()
                apps.forEach { array.put(appJson(context, it)) }
                row(JSONObject().put("schema_version", 1).put("apps", array).toString())
            }
            "state" -> {
                val pkg = uri.pathSegments.getOrNull(1) ?: return row("{}")
                val app = apps.firstOrNull { it.packageName == pkg } ?: return row("{}")
                row(appJson(context, app).toString())
            }
            "plan" -> {
                val pkg = uri.pathSegments.getOrNull(1) ?: return row("{}")
                row(buildPlan(context, apps.firstOrNull { it.packageName == pkg }))
            }
            else -> row("{}")
        }
    }

    /**
     * A plan is a description, not a command: it lists which entry inside the APK would be
     * replaced and where the replacement comes from. The platform validates every field against
     * its own whitelist before it does anything.
     */
    private fun buildPlan(context: Context, app: CatalogApp?): String {
        if (app == null) return JSONObject().put("error", "unknown package").toString()
        val identity = CobaltProfiles.installed(context, app.packageName)
            ?: return JSONObject().put("error", "not installed").toString()

        val ops = JSONArray()
        app.platformLayers.filter { it.isUsable }.forEach { layer ->
            ops.put(
                JSONObject()
                    .put("type", "replace_entry")
                    .put("entry", layer.payloadEntry.ifBlank { "resources.arsc" })
                    .put("url", BASE + layer.path)
                    .put("sha256", layer.sha256)
                    .put("bytes", layer.bytes),
            )
        }
        val runtime = app.bestRuntime
        return JSONObject()
            .put("schema_version", 1)
            .put("package", app.packageName)
            .put("version_name", identity.versionName)
            .put("version_code", identity.versionCode)
            .put("verified", app.compatibility.isVerified(identity.versionName))
            .put("ops", ops)
            .put("runtime", JSONObject().apply {
                put("dictionary", runtime != null)
                put("layer", runtime?.contentId ?: "")
            })
            .toString()
    }

    override fun getType(uri: Uri): String = "application/json"

    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException("read-only")

    override fun delete(uri: Uri, selection: String?, args: Array<String>?): Int = throw UnsupportedOperationException("read-only")

    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<String>?): Int = throw UnsupportedOperationException("read-only")

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle = throw UnsupportedOperationException("read-only")
}
