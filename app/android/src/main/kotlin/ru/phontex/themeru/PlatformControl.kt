package ru.phontex.themeru

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Sends the selected target role to Cobalt Platform without passing arbitrary shell commands. */
object PlatformControl {
    suspend fun configure(packageName: String, versionName: String, versionCode: Long, features: Set<String>): String? = withContext(Dispatchers.IO) {
        val payload = JSONObject().put("package", packageName).put("version_name", versionName).put("version_code", versionCode).put("translation_mode", "lsposed").put("features", features.toList()).toString()
        val encoded = Base64.encodeToString(payload.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val command = "if [ -x /data/adb/modules/cobalt-platform/action.sh ]; then /data/adb/modules/cobalt-platform/action.sh configure '$encoded'; elif [ -x /data/adb/modules_update/cobalt-platform/action.sh ]; then /data/adb/modules_update/cobalt-platform/action.sh configure '$encoded'; else exit 127; fi"
        runCatching {
            val process = ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
            if (!process.waitFor(8, TimeUnit.SECONDS)) { process.destroyForcibly(); "Cobalt Platform не ответил вовремя" }
            else if (process.exitValue() != 0) "Cobalt Platform не установлен или отклоняет профиль" else null
        }.getOrElse { "Root-доступ недоступен" }
    }
}
