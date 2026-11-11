package ru.phontex.themeru

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.annotation.SuppressLint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class InstalledAppIdentity(
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val signingSha256: String?,
)

data class CobaltAppProfile(
    val id: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long?,
    val path: String,
)

object CobaltProfiles {
    const val CATALOG_URL = "https://raw.githubusercontent.com/phontex/cobalt/main/manifests/profiles.json"
    val builtIn = listOf(
        CobaltAppProfile("heytap-themestore-17.11.18", "com.heytap.themestore", "17.11.18", null, "profiles/com.heytap.themestore/17.11.18/profile.json"),
        CobaltAppProfile("heytap-themestore-17.22.1", "com.heytap.themestore", "17.22.1", 1722010L, "profiles/com.heytap.themestore/17.22.1/profile.json"),
        CobaltAppProfile("oplus-themestore-EXP_17.11.20", "com.oplus.themestore", "EXP_17.11.20", 1711200L, "profiles/com.oplus.themestore/EXP_17.11.20/profile.json"),
    )

    @SuppressLint("InlinedApi")
    fun installed(context: Context, packageName: String): InstalledAppIdentity? = runCatching {
        val pm = context.packageManager
        val info = if (Build.VERSION.SDK_INT >= 33) pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())) else @Suppress("DEPRECATION") pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        val signing = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()?.let(::sha256) else null
        InstalledAppIdentity(packageName, info.versionName.orEmpty(), if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong(), signing)
    }.getOrNull()

    fun resolve(identity: InstalledAppIdentity, profiles: List<CobaltAppProfile> = builtIn): CobaltAppProfile? = profiles.firstOrNull { p -> p.packageName == identity.packageName && p.versionName == identity.versionName && (p.versionCode == null || p.versionCode == identity.versionCode) }

    suspend fun loadRemote(): List<CobaltAppProfile>? = withContext(Dispatchers.IO) { runCatching {
        val conn = (URL(CATALOG_URL).openConnection() as HttpURLConnection).apply { connectTimeout = 8_000; readTimeout = 12_000; requestMethod = "GET" }
        conn.inputStream.bufferedReader().use { reader ->
            val root = JSONObject(reader.readText()); val result = mutableListOf<CobaltAppProfile>(); val array = root.optJSONArray("profiles") ?: return@use result
            for (i in 0 until array.length()) { val o = array.getJSONObject(i); result += CobaltAppProfile(o.getString("id"), o.getString("package"), o.getString("version_name"), if (o.isNull("version_code")) null else o.getLong("version_code"), o.getString("path")) }
            result
        }.also { conn.disconnect() }
    }.getOrNull() }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
