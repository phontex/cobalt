package com.cobalt.app

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
        CobaltAppProfile("heytap-themestore-17.23.0", "com.heytap.themestore", "17.23.0", 1723003L, "profiles/com.heytap.themestore/17.23.0/profile.json"),
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

    /**
     * Picks the profile to use for an installed application.
     *
     * The version is advisory, not a gate: an exact match is preferred, then the same package
     * with the closest version, then the package alone. A version the catalogue has not verified
     * must still translate — the caller is expected to warn the user, not to refuse.
     */
    fun resolve(identity: InstalledAppIdentity, profiles: List<CobaltAppProfile> = builtIn): CobaltAppProfile? {
        val samePackage = profiles.filter { it.packageName == identity.packageName }
        if (samePackage.isEmpty()) return null
        return samePackage.firstOrNull { it.versionName == identity.versionName && (it.versionCode == null || it.versionCode == identity.versionCode) }
            ?: samePackage.firstOrNull { it.versionName == identity.versionName }
            ?: samePackage.minByOrNull { versionDistance(it.versionName, identity.versionName) }
    }

    /** Rough ordering distance used only to choose the nearest profile; never gates anything. */
    private fun versionDistance(left: String, right: String): Long {
        fun parts(v: String) = v.removePrefix("v").substringBefore('-').split('.', '_')
            .map { it.filter(Char::isDigit).toLongOrNull() ?: 0L }
        val a = parts(left); val b = parts(right)
        var distance = 0L
        for (i in 0 until maxOf(a.size, b.size)) {
            distance = distance * 1000 + kotlin.math.abs(a.getOrElse(i) { 0L } - b.getOrElse(i) { 0L }).coerceAtMost(999)
        }
        return distance
    }

    /** True when the catalogue has an entry for this exact build. */
    fun isVerified(identity: InstalledAppIdentity, profiles: List<CobaltAppProfile> = builtIn): Boolean =
        profiles.any { it.packageName == identity.packageName && it.versionName == identity.versionName }

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
