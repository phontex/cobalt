package com.cobalt.app

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Result of a catalog refresh: where the data came from and when it was last updated. */
data class CatalogState(
    val catalog: Catalog?,
    val fromCache: Boolean,
    val fetchedAt: Long,
    val error: String? = null,
) {
    val apps: List<CatalogApp> get() = catalog?.apps.orEmpty()
}

/**
 * Fetches and caches the Cobalt catalog.
 *
 * The last successful response is written to `files/catalog/catalog.json`, so the update screen
 * and the supported-application list keep working without a network connection. A failed
 * refresh never discards the cache: [refresh] returns the cached copy together with the error
 * message.
 */
object CatalogRepository {
    private const val TIMEOUT = 20_000

    private fun file(context: Context) = File(File(context.filesDir, "catalog"), "catalog.json")

    fun cached(context: Context): CatalogState {
        val f = file(context)
        if (!f.isFile) return CatalogState(null, fromCache = true, fetchedAt = 0L)
        return runCatching {
            CatalogState(Catalog.parse(JSONObject(f.readText())), fromCache = true, fetchedAt = f.lastModified())
        }.getOrElse { CatalogState(null, fromCache = true, fetchedAt = 0L, error = "Кэш каталога повреждён") }
    }

    suspend fun refresh(context: Context): CatalogState = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL(Catalog.URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT
                readTimeout = TIMEOUT
                requestMethod = "GET"
                instanceFollowRedirects = true
            }
            val body = try {
                if (connection.responseCode !in 200..299) {
                    throw IllegalStateException("Сервер вернул ${connection.responseCode}")
                }
                connection.inputStream.use { it.readBytes() }
            } finally {
                connection.disconnect()
            }
            val catalog = Catalog.parse(JSONObject(String(body, Charsets.UTF_8)))
            if (catalog.apps.isEmpty()) throw IllegalStateException("Каталог не содержит приложений")

            val f = file(context)
            f.parentFile?.mkdirs()
            val temp = File(f.parentFile, f.name + ".part")
            temp.writeBytes(body)
            if (!temp.renameTo(f)) {
                f.delete()
                temp.renameTo(f)
            }
            CatalogState(catalog, fromCache = false, fetchedAt = System.currentTimeMillis())
        }.getOrElse { error ->
            val fallback = cached(context)
            fallback.copy(error = error.message ?: "Не удалось получить каталог")
        }
    }

    /** Applications from the catalog that are actually installed on this device. */
    suspend fun installedApps(context: Context, state: CatalogState): List<CatalogApp> = withContext(Dispatchers.IO) {
        state.apps.filter { CobaltProfiles.installed(context, it.packageName) != null }
    }

    /** Compatibility data for one package from the cached catalog; never touches the network. */
    suspend fun cachedCompatibility(context: Context, packageName: String): Compatibility? =
        withContext(Dispatchers.IO) { cached(context).catalog?.app(packageName)?.compatibility }
}
