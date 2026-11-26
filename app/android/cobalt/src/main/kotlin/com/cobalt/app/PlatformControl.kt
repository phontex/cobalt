package com.cobalt.app

import androidx.core.content.edit

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Thin, explicit bridge to Cobalt Platform.
 *
 * Every call maps to one documented platform command. Nothing here builds a shell command from
 * user data: payloads travel as base64 JSON passed as a single quoted argument, and file paths
 * are validated before they are used.
 */
object PlatformControl {
    private val roots = listOf(
        "/data/adb/modules/cobalt-platform",
        "/data/adb/modules_update/cobalt-platform",
        "/data/adb/lspd/cobalt-platform",
    )

    fun isInstalled(): Boolean = roots.any { File(it).isDirectory }

    private suspend fun run(vararg args: String): String? = withContext(Dispatchers.IO) {
        val command = buildString {
            append("if [ -x /data/adb/modules/cobalt-platform/action.sh ]; then ")
            append("/data/adb/modules/cobalt-platform/action.sh")
            args.forEach { append(" '").append(it.replace("'", "'\\''")).append("'") }
            append("; elif [ -x /data/adb/modules_update/cobalt-platform/action.sh ]; then ")
            append("/data/adb/modules_update/cobalt-platform/action.sh")
            args.forEach { append(" '").append(it.replace("'", "'\\''")).append("'") }
            append("; else exit 127; fi")
        }
        runCatching {
            val su = listOf("/system/bin/su", "/system/xbin/su", "/sbin/su", "su")
                .firstOrNull { it == "su" || File(it).canExecute() } ?: "su"
            val process = ProcessBuilder(su, "-c", command).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                null
            } else if (process.exitValue() != 0) null else output.trim()
        }.getOrNull()
    }

    /** Stores the role Cobalt takes for this package; the platform keeps it for the next boot. */
    /**
     * The token that lets the platform read the bridge. It is generated once, kept in the app's
     * private preferences, and only ever handed to the platform through `configure`, which runs
     * under root. Without it the bridge refuses every request.
     */
    fun token(context: Context): String {
        val prefs = context.getSharedPreferences("local", 0)
        prefs.getString("platform_token", null)?.takeIf { it.isNotBlank() }?.let { return it }
        val fresh = java.util.UUID.randomUUID().toString().replace("-", "")
        prefs.edit { putString("platform_token", fresh) }
        return fresh
    }

    suspend fun configure(context: Context, packageName: String, versionName: String, versionCode: Long, features: Set<String>): String? {
        val payload = JSONObject()
            .put("token", token(context))
            .put("package", packageName)
            .put("version_name", versionName)
            .put("version_code", versionCode)
            .put("translation_mode", "lsposed")
            .put("features", features.toList())
            .toString()
        val encoded = Base64.encodeToString(payload.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        return run("configure", encoded) ?: "Cobalt Platform не принял профиль"
    }

    /** Version, code, APK path and splits of an installed package, straight from the platform. */
    suspend fun discover(packageName: String): JSONObject? =
        run("discover", packageName)?.let { runCatching { JSONObject(it) }.getOrNull() }

    /**
     * Copies the installed APK where the app can read it.
     *
     * Cobalt App cannot read `/data/app` itself, so the platform exports the file into the app's
     * own storage. The platform only accepts destinations inside this application or
     * `/data/local/tmp`, so the command cannot be used to write anywhere else.
     */
    suspend fun exportApk(packageName: String, destination: String, apkName: String = "base.apk"): JSONObject? =
        (if (apkName == "base.apk") run("export", packageName, destination)
        else run("export", packageName, destination, apkName))
            ?.let { runCatching { JSONObject(it) }.getOrNull() }

    /**
     * Mounts [apkPath] over the installed base or split APK.
     *
     * The version is advisory: a mismatch is reported back in the `warning` field of the
     * response and recorded by the platform, but the mount still happens, because a translation
     * for a neighbouring build is usually better than none.
     */
    suspend fun mount(
        packageName: String,
        apkPath: String,
        expectedVersion: String,
        apkName: String = "base.apk",
    ): JSONObject? =
        (if (apkName == "base.apk") run("mount", packageName, apkPath, expectedVersion)
        else run("mount", packageName, apkPath, expectedVersion, apkName))
            ?.let { runCatching { JSONObject(it) }.getOrNull() }

    suspend fun umount(packageName: String): String? =
        run("umount", packageName) ?: "Не удалось снять подмену"

    suspend fun verify(packageName: String): JSONObject? =
        run("verify", packageName)?.let { runCatching { JSONObject(it) }.getOrNull() }

    /** True when the platform directory exists and the binary answers. */
    suspend fun status(context: Context): JSONObject? = run("status")?.let { runCatching { JSONObject(it) }.getOrNull() }
}
