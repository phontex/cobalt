package com.cobalt.app

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import java.util.UUID

/** Applies the editable platform translation through Cobalt Platform. */
object PatchInstaller {
    private const val TIMEOUT = 30_000
    private const val BASE = "https://raw.githubusercontent.com/phontex/cobalt/main/"

    private fun work(context: Context): File = File(
        context.cacheDir,
        "patch/${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(8)}",
    ).apply { mkdirs() }

    data class Progress(val stage: String, val fraction: Float)

    /**
     * Downloads a text translation map, patches the installed APK's resource pool locally, and
     * asks the platform to mount the result. The installed base APK is never modified in place.
     */
    suspend fun apply(
        context: Context,
        packageName: String,
        layer: TranslationLayer,
        onProgress: (Progress) -> Unit = {},
        onWarning: (String) -> Unit = {},
    ): Result<JSONObject> = withContext(Dispatchers.IO) {
        runCatching {
            require(layer.isPayload && layer.path.isNotBlank() && layer.sha256.isNotBlank()) {
                "В каталоге нет редактируемого файла ресурсов"
            }
            require(PlatformControl.status(context)?.optBoolean("enabled", false) == true) {
                "Cobalt Platform не установлен или не отвечает"
            }
            val identity = CobaltProfiles.installed(context, packageName)
                ?: throw IllegalStateException("Приложение не установлено")

            val workDir = work(context)
            onProgress(Progress("Загрузка переводов ресурсов", 0f))
            val sourceBytes = download(resolveUrl(layer.path)) {
                onProgress(Progress("Загрузка переводов ресурсов", it * .25f))
            }
            if (!sha256Hex(sourceBytes).equals(layer.sha256, ignoreCase = true)) {
                throw IllegalStateException("Контрольная сумма перевода ресурсов не совпала")
            }
            val translations = parseTranslations(sourceBytes)
            require(translations.isNotEmpty()) { "Файл ресурсов не содержит переводов" }

            val discovered = PlatformControl.discover(packageName)
            val apkNames = discovered?.optString("splits").orEmpty()
                .split(';')
                .map { it.substringAfterLast('/').trim() }
                .filter { it.endsWith(".apk") && !it.startsWith("split_config.") }
                .distinct()
                .ifEmpty { listOf("base.apk") }
                .sortedBy { if (it == "base.apk") 0 else 1 }

            val entry = layer.payloadEntry.ifBlank { "resources.arsc" }
            var mountedCount = 0
            var replacedTotal = 0
            var lastResponse: JSONObject? = null
            val stepSpan = 0.70f / apkNames.size

            val baseAssetOverrides = mapOf(
                "assets/htmls/font/sys.ttf" to context.resources.openRawResource(R.font.cobalt_sans).use { it.readBytes() },
            )

            apkNames.forEachIndexed { idx, apkName ->
                val stepStart = 0.25f + idx * stepSpan
                val isBase = apkName == "base.apk"
                onProgress(Progress(if (isBase) "Копирование APK" else "Копирование $apkName", stepStart))
                val srcFile = File(workDir, "src_$apkName")
                srcFile.delete()
                val export = PlatformControl.exportApk(packageName, srcFile.absolutePath, apkName)
                if (export == null || !export.optBoolean("ok")) {
                    if (isBase) throw IllegalStateException(export?.optString("error").orEmpty().ifBlank { "Не удалось получить APK с устройства" })
                    return@forEachIndexed
                }

                onProgress(Progress(if (isBase) "Патч ресурсов" else "Патч $apkName", stepStart + stepSpan * 0.2f))
                val patched = File(workDir, "patched_$apkName")
                patched.delete()
                val replaced = patchApk(
                    source = srcFile,
                    target = patched,
                    entryName = entry,
                    translations = translations,
                    assetOverrides = if (isBase) baseAssetOverrides else emptyMap(),
                    required = isBase,
                ) { progress ->
                    onProgress(Progress(if (isBase) "Патч ресурсов" else "Патч $apkName", stepStart + stepSpan * (0.2f + progress * 0.6f)))
                }
                srcFile.delete()
                if (replaced == 0) {
                    patched.delete()
                    return@forEachIndexed
                }

                onProgress(Progress(if (isBase) "Применение" else "Применение $apkName", stepStart + stepSpan * 0.85f))
                val response = PlatformControl.mount(packageName, patched.absolutePath, identity.versionName, apkName)
                patched.delete()
                if (response == null || !response.optBoolean("ok", true)) {
                    if (isBase) throw IllegalStateException("Платформа не смогла применить ресурсы")
                    return@forEachIndexed
                }
                response.optString("warning").takeIf { it.isNotBlank() }?.let(onWarning)
                mountedCount++
                replacedTotal += replaced
                lastResponse = response
            }

            if (mountedCount == 0) throw IllegalStateException("Не удалось применить ресурсы ни к одному APK")
            onProgress(Progress("Готово", 1f))
            workDir.deleteRecursively()
            (lastResponse ?: JSONObject().put("ok", true))
                .put("mounted_apks", mountedCount)
                .put("replaced_total", replacedTotal)
        }
    }

    private fun parseTranslations(bytes: ByteArray): Map<String, String> {
        val root = JSONObject(String(bytes, Charsets.UTF_8))
        val source = root.optJSONObject("entries")
            ?: root.optJSONObject("sections")?.optJSONObject("resources")
            ?: throw IllegalStateException("В файле нет entries или sections.resources")
        val result = LinkedHashMap<String, String>()
        val keys = source.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = source.optString(key)
            if (key.isNotEmpty() && value.isNotEmpty() && key != value) result[key] = value
        }
        return result
    }

    private fun patchApk(
        source: File,
        target: File,
        entryName: String,
        translations: Map<String, String>,
        assetOverrides: Map<String, ByteArray>,
        required: Boolean,
        onProgress: (Float) -> Unit,
    ): Int {
        var found = false
        var replaced = 0
        ZipFile(source).use { zip ->
            val targetEntry = zip.getEntry(entryName)
            if (targetEntry == null) {
                if (required) throw IllegalStateException("В APK нет записи $entryName")
                return 0
            }
            val originalArsc = zip.getInputStream(targetEntry).use { it.readBytes() }
            val patchResult = ArscStringPoolPatcher.patch(originalArsc, translations)
            replaced = patchResult.replaced
            if (replaced == 0) {
                if (required) throw IllegalStateException("В resources.arsc не найдено ни одной строки из каталога")
                return 0
            }
            ZipOutputStream(target.outputStream().buffered()).use { out ->
                val entries = zip.entries().toList()
                entries.forEachIndexed { index, item ->
                    val data = when {
                        item.name == entryName -> {
                            found = true
                            patchResult.bytes
                        }
                        assetOverrides.containsKey(item.name) -> assetOverrides.getValue(item.name)
                        else -> zip.getInputStream(item).use { it.readBytes() }
                    }
                    writeEntry(out, item, data)
                    onProgress((index + 1).toFloat() / entries.size)
                }
            }
        }
        if (required) check(found) { "В APK нет записи $entryName" }
        return replaced
    }

    private fun writeEntry(out: ZipOutputStream, original: ZipEntry, data: ByteArray) {
        val entry = ZipEntry(original.name).apply {
            time = original.time
            comment = original.comment
            extra = original.extra
            method = if (original.name == "resources.arsc") ZipEntry.STORED else original.method
        }
        if (entry.method == ZipEntry.STORED) {
            entry.size = data.size.toLong()
            entry.compressedSize = data.size.toLong()
            entry.crc = java.util.zip.CRC32().apply { update(data) }.value
        }
        out.putNextEntry(entry)
        out.write(data)
        out.closeEntry()
    }

    private fun resolveUrl(path: String): String =
        if (path.startsWith("https://", ignoreCase = true)) path else BASE + path

    private fun download(url: String, onProgress: (Float) -> Unit): ByteArray {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT
            readTimeout = TIMEOUT
            instanceFollowRedirects = true
        }
        return try {
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("Сервер вернул ${connection.responseCode}")
            }
            val total = connection.contentLengthLong
            val output = ByteArrayOutputStream(if (total in 1..Int.MAX_VALUE) total.toInt() else 1 shl 20)
            connection.inputStream.use { input ->
                val buffer = ByteArray(64 * 1024)
                var completed = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    completed += read
                    if (total > 0) onProgress((completed.toFloat() / total).coerceIn(0f, 1f))
                }
            }
            output.toByteArray()
        } finally {
            connection.disconnect()
        }
    }
}
