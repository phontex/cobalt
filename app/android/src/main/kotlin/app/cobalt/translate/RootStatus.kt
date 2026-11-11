package app.cobalt.translate

import android.content.Context
import kotlinx.coroutines.Dispatchers
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
        "app.cobalt.platform",
        "app.cobalt.helper",
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
            xposedBridgeLoaded -> CobaltCheckState.ACTIVE
            lsposedMarker -> CobaltCheckState.ACTIVE
            managerInstalled -> CobaltCheckState.UNKNOWN
            root.first -> CobaltCheckState.INACTIVE
            else -> CobaltCheckState.UNKNOWN
        }
        val helperPackage = PLATFORM_PACKAGES.any { packageInstalled(context, it) }
        val helperMarker = root.first && hasMarker(root.second, PLATFORM_MARKERS)
        val helper = when {
            helperPackage || helperMarker -> CobaltCheckState.ACTIVE
            root.first -> CobaltCheckState.INACTIVE
            else -> CobaltCheckState.UNKNOWN
        }
        val detail = when {
            root.first && lsposed == CobaltCheckState.ACTIVE -> "Root и LSPosed подтверждены"
            root.first -> "Root подтверждён; LSPosed не подтверждён"
            else -> "Для проверки части статусов требуется root"
        }
        CobaltRuntimeStatus(
            root = if (root.first) CobaltCheckState.ACTIVE else CobaltCheckState.INACTIVE,
            lsposed = lsposed,
            platform = helper,
            detail = detail,
        )
    }

    private fun packageInstalled(context: Context, packageName: String): Boolean =
        runCatching { context.packageManager.getPackageInfo(packageName, 0); true }.getOrDefault(false)

    private fun checkRoot(): Pair<Boolean, String> {
        return try {
            val process = ProcessBuilder("su", "-c", "id")
                .redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val completed = process.waitFor(1500, TimeUnit.MILLISECONDS)
            if (!completed) { process.destroyForcibly(); false to "" }
            else (process.exitValue() == 0 && output.contains("uid=0")) to output
        } catch (_: Throwable) { false to "" }
    }

    private fun hasMarker(rootOutput: String, markers: List<String>): Boolean {
        // Root checks are intentionally performed through a fixed, non-destructive command.
        // The output is supplemented by readable paths when the device permits it.
        return markers.any { File(it).exists() } || markers.any { rootOutput.contains(it) }
    }
}
