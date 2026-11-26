package com.cobalt.app

import android.content.Context
import kotlinx.coroutines.Dispatchers
import org.json.JSONObject
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/** A deliberately conservative status: unknown is never presented as enabled. */
enum class CobaltCheckState { CHECKING, ACTIVE, INACTIVE, UNKNOWN }

data class CobaltRuntimeStatus(
    val root: CobaltCheckState = CobaltCheckState.CHECKING,
    val lsposed: CobaltCheckState = CobaltCheckState.CHECKING,
    val platform: CobaltCheckState = CobaltCheckState.CHECKING,
    val detail: String = "Проверка состояния…",
) {
    companion object { fun loading() = CobaltRuntimeStatus() }
}

/**
 * Runtime checks used by the connection card.
 *
 * The helper module contract is intentionally separate from the current Cobalt module. Future
 * builds may identify it by one of PLATFORM_PACKAGES or by a root/LSPosed marker directory.
 * The current app package is never used as evidence for helper-module availability.
 */
object CobaltRuntimeChecker {
    private val PLATFORM_PACKAGES = setOf(
        "com.cobalt.platform",
        "com.cobalt.helper",
        "com.phontex.cobalt.platform",
    )
    private val PLATFORM_MARKERS = listOf(
        "/data/adb/modules/cobalt-platform",
        "/data/adb/modules_update/cobalt-platform",
        "/data/adb/lspd/cobalt-platform",
    )
    private val LSPOSED_MARKERS = listOf(
        "/data/adb/lspd",
        "/data/adb/modules/zygisk_lsposed",
        "/data/adb/modules/lsposed",
    )

    suspend fun check(context: Context): CobaltRuntimeStatus = withContext(Dispatchers.IO) {
        val root = checkRoot()
        val platformStatus = runCatching { PlatformControl.status(context) }.getOrNull()
        val platformResponds = statusFlag(platformStatus, "enabled")
        // The shell's JSON contract emits 0/1; optBoolean() does not reliably coerce integers.
        val platformRoot = statusFlag(platformStatus, "root")
        val hasPlatformLsposed = platformStatus?.has("lsposed") == true
        val platformLsposed = statusFlag(platformStatus, "lsposed")
        val managerInstalled = runCatching {
            context.packageManager.getPackageInfo("org.lsposed.manager", 0)
            true
        }.getOrDefault(false)
        val xposedBridgeLoaded = runCatching {
            Class.forName("de.robv.android.xposed.XposedBridge")
            true
        }.getOrDefault(false)
        val lsposedMarker = root.first && hasMarker(root.second, LSPOSED_MARKERS)
        val lsposed = when {
            platformResponds && hasPlatformLsposed && platformLsposed -> CobaltCheckState.ACTIVE
            platformResponds && !hasPlatformLsposed -> CobaltCheckState.ACTIVE
            xposedBridgeLoaded -> CobaltCheckState.ACTIVE
            lsposedMarker -> CobaltCheckState.ACTIVE
            managerInstalled -> CobaltCheckState.UNKNOWN
            root.first -> CobaltCheckState.INACTIVE
            else -> CobaltCheckState.UNKNOWN
        }
        val helperPackage = PLATFORM_PACKAGES.any { packageInstalled(context, it) }
        val helperMarker = root.first && hasMarker(root.second, PLATFORM_MARKERS)
        val helper = when {
            platformResponds -> CobaltCheckState.ACTIVE
            helperPackage || helperMarker -> CobaltCheckState.ACTIVE
            root.first -> CobaltCheckState.INACTIVE
            else -> CobaltCheckState.UNKNOWN
        }
        val detail = when {
            platformResponds && lsposed == CobaltCheckState.ACTIVE -> "Root, LSPosed и Cobalt Platform подтверждены"
            root.first && lsposed == CobaltCheckState.ACTIVE -> "Root и LSPosed подтверждены"
            root.first -> "Root подтверждён; LSPosed не подтверждён"
            else -> "Для проверки части статусов требуется root"
        }
        CobaltRuntimeStatus(
            // A successful authenticated platform status already proves that the app obtained
            // root. Do not require a second filesystem probe, which is denied by Android's
            // mount namespace even when su works.
            root = if (platformResponds || platformRoot || root.first) CobaltCheckState.ACTIVE else CobaltCheckState.INACTIVE,
            lsposed = if (platformResponds && (!hasPlatformLsposed || platformLsposed)) CobaltCheckState.ACTIVE else lsposed,
            platform = helper,
            detail = detail,
        )
    }

    private fun statusFlag(status: JSONObject?, key: String): Boolean = when (val value = status?.opt(key)) {
        is Boolean -> value
        is Number -> value.toInt() != 0
        is String -> value.equals("true", ignoreCase = true) || value == "1"
        else -> false
    }

    private fun packageInstalled(context: Context, packageName: String): Boolean =
        runCatching { context.packageManager.getPackageInfo(packageName, 0); true }.getOrDefault(false)

    private fun checkRoot(): Pair<Boolean, String> {
        return try {
            // Read-only, fixed probe: ask root to report the markers that the app UID cannot
            // inspect under /data/adb. No paths or commands come from network/user data.
            val command = "id; for p in " +
                (LSPOSED_MARKERS + PLATFORM_MARKERS).joinToString(" ") +
                "; do [ -d \"\$p\" ] && echo \"COBALT_MARKER:\$p\"; done"
            val su = when {
                File("/system/bin/su").canExecute() -> "/system/bin/su"
                File("/system/xbin/su").canExecute() -> "/system/xbin/su"
                else -> "su"
            }
            val process = ProcessBuilder(su, "-c", command)
                .redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val completed = process.waitFor(2500, TimeUnit.MILLISECONDS)
            if (!completed) { process.destroyForcibly(); false to output }
            else (process.exitValue() == 0 && output.contains("uid=0")) to output
        } catch (_: Throwable) { false to "" }
    }

    private fun hasMarker(rootOutput: String, markers: List<String>): Boolean {
        // Root checks are intentionally performed through a fixed, non-destructive command.
        // The output is supplemented by readable paths when the device permits it.
        return markers.any { File(it).exists() } || markers.any { rootOutput.contains(it) }
    }
}
