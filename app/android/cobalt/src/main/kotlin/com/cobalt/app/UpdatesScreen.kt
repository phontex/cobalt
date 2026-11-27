package com.cobalt.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Updates screen.
 *
 * Two layers per application, because they are two different responsibilities:
 *
 * - «Перевод интерфейса» — served by Cobalt App, replaced at runtime by the LSPosed part;
 * - «Ресурсы APK» — served by Cobalt Platform, applied as a payload over the installed APK.
 *
 * The application version is advisory. A build the catalogue has not verified still gets the
 * translation, with a visible warning instead of a refusal.
 */
private data class PendingTranslationUpdate(
    val packageName: String,
    val layer: TranslationLayer,
    val isPlatform: Boolean,
)

@Composable
internal fun ColumnScope.UpdatesContent(
    context: android.content.Context,
    focusPackage: String? = null,
    onBack: () -> Unit,
    onChanged: () -> Unit,
) {
    val c = LocalDesign.current
    val scope = rememberCoroutineScope()

    var state by remember { mutableStateOf<CatalogState?>(null) }
    var checking by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val progress = remember { mutableStateMapOf<String, Float>() }
    val busy = remember { mutableStateMapOf<String, Boolean>() }
    val patchBusy = remember { mutableStateMapOf<String, Boolean>() }
    val patchStage = remember { mutableStateMapOf<String, String>() }
    val patchProgress = remember { mutableStateMapOf<String, Float>() }
    var patchApplied by remember { mutableStateOf<Set<String>>(emptySet()) }
    var installed by remember { mutableStateOf<Map<String, InstalledDictionary>>(emptyMap()) }
    var pendingUpdate by remember { mutableStateOf<PendingTranslationUpdate?>(null) }
    var pendingLog by remember { mutableStateOf<TranslationChangeLog?>(null) }
    var checkingInfo by remember { mutableStateOf(false) }

    fun reloadInstalled() {
        installed = KNOWN_PACKAGES
            .mapNotNull { pkg -> DictionaryInstaller.installed(context, pkg)?.let { pkg to it } }
            .toMap()
    }

    fun reloadPatchStatus() {
        scope.launch {
            val mounted = KNOWN_PACKAGES.filter { pkg ->
                PlatformControl.verify(pkg)?.optBoolean("mounted", false) == true
            }.toSet()
            patchApplied = mounted
        }
    }

    fun check() {
        if (checking) return
        checking = true
        message = null
        scope.launch {
            val result = CatalogRepository.refresh(context)
            state = result
            checking = false
            message = result.error ?: if (result.fromCache) "Показан сохранённый каталог" else null
            reloadInstalled()
            reloadPatchStatus()
            onChanged()
        }
    }

    LaunchedEffect(Unit) {
        reloadInstalled()
        reloadPatchStatus()
        state = CatalogRepository.cached(context)
        if (state?.catalog == null) check()
        else check()
    }

    val current = state
    val apps = remember(current, installed) {
        current?.apps.orEmpty().filter { CobaltProfiles.installed(context, it.packageName) != null &&
            (focusPackage == null || it.packageName == focusPackage) }
    }

    val focusName = focusPackage?.let { current?.catalog?.app(it)?.name }
    SoftPageTitle(
        title = if (focusName == null) "Обновления" else "Обновления приложения",
        subtitle = focusName ?: "Переводы загружаются отдельно от приложения",
    )
    Spacer(Modifier.height(14.dp))

    SoftGroup {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = SoftSpace.card, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = when {
                        checking -> "Проверка…"
                        current?.error != null -> "Не удалось проверить"
                        else -> "Каталог обновлён"
                    },
                    label = "updateState",
                ) { text ->
                    Text(text, style = CobaltType.bodyStrong, color = c.text, maxLines = 1)
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    listOfNotNull(
                        current?.catalog?.updated?.takeIf { it.isNotBlank() }?.let { "Каталог от $it" },
                        current?.error,
                    ).joinToString(" · ").ifBlank { "Проверка идёт по каталогу Cobalt на GitHub" },
                    style = CobaltType.caption,
                    color = if (current?.error != null) c.danger else c.muted,
                )
            }
            SoftTextAction(if (checking) "Проверка…" else "Проверить снова", enabled = !checking, onClick = ::check)
        }
    }

    if (apps.isEmpty()) {
        SoftNote(
            "Ни одно приложение из каталога не установлено. Проверка выполняется на устройстве, " +
                "список приложений никуда не отправляется.",
            Modifier.padding(top = 14.dp),
        )
    }

    apps.forEach { app ->
        val key = app.packageName
        val identity = CobaltProfiles.installed(context, app.packageName)
        val verified = identity != null && app.compatibility.isVerified(identity.versionName)
        val versionWarning = identity?.let { app.compatibility.rangeWarning(it.versionName) }
        val has = installed[app.packageName]
        val runtime = app.bestRuntime
        val upToDate = has != null && runtime != null && has.contentId == runtime.contentId

        SoftSectionTitle(app.name)

        if (identity != null && !verified) {
            SoftNote(
                versionWarning ?: "Версия ${identity.versionName} ещё не проверена. " +
                    "Перевод всё равно будет применён; некоторые строки могут остаться без перевода.",
                Modifier.padding(bottom = 12.dp),
            )
        }

        SoftGroup {
            // Layer 1: runtime translation, served by the app.
            Column(Modifier.padding(horizontal = SoftSpace.card, vertical = 12.dp)) {
                Text("Перевод интерфейса", style = CobaltType.bodyStrong, color = c.text)
                Spacer(Modifier.height(4.dp))
                Text(
                    when {
                        has != null && upToDate -> "Установлено: ${has.describeText()}"
                        has != null && runtime != null -> "Установлено: ${has.describeText()}\nДоступно: ${runtime.describe()}"
                        runtime != null -> "Доступен: ${runtime.describe()}"
                        else -> "Перевода пока нет"
                    },
                    style = CobaltType.caption,
                    color = c.muted,
                )
                if (runtime?.changeSummary?.isNotBlank() == true) {
                    Spacer(Modifier.height(4.dp))
                    Text("Изменения: ${runtime.changeSummary}", style = CobaltType.caption, color = c.muted)
                }
            }
            val isBusy = busy[key] == true
            if (runtime != null && !upToDate) {
                SoftDivider(start = 0.dp, end = 0.dp)
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = SoftSpace.card + 6.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SoftAction(
                        text = if (isBusy) "Загрузка…" else if (has == null) "Загрузить" else "Обновить",
                        enabled = !isBusy,
                        modifier = Modifier.widthIn(min = 156.dp, max = 220.dp),
                    ) {
                        if (isBusy) return@SoftAction
                        pendingUpdate = PendingTranslationUpdate(app.packageName, runtime, false)
                        checkingInfo = true
                        scope.launch {
                            pendingLog = state?.catalog?.let { UpdateRepository.fetchTranslationChanges(
                                catalog = it,
                                packageName = app.packageName,
                                layerName = if (runtime.servedBy == ServedBy.APP) "app" else "platform",
                            ).getOrNull() }
                            checkingInfo = false
                        }
                    }
                    SoftTextAction("Информация об обновлении", enabled = !isBusy) {
                        pendingUpdate = PendingTranslationUpdate(app.packageName, runtime, false)
                        checkingInfo = true
                        scope.launch {
                            pendingLog = state?.catalog?.let { UpdateRepository.fetchTranslationChanges(
                                catalog = it,
                                packageName = app.packageName,
                                layerName = if (runtime.servedBy == ServedBy.APP) "app" else "platform",
                            ).getOrNull() }
                            checkingInfo = false
                        }
                    }
                }
                val p = progress[key]
                if (isBusy && p != null) LinearProgress(p)
            } else if (has != null) {
                SoftDivider(start = 0.dp, end = 0.dp)
                SoftRow(
                    title = "Удалить перевод",
                    summary = "Вернуться к оригинальному тексту приложения",
                    showChevron = false,
                    onClick = {
                        DictionaryInstaller.remove(context, app.packageName)
                        reloadInstalled()
                        onChanged()
                        message = "Перевод удалён"
                    },
                )
            }
        }

        Spacer(Modifier.height(SoftSpace.group))

        // Layer 2: APK resources, applied by Cobalt Platform.
        val payload = app.bestPayload
        SoftGroup {
            Column(Modifier.padding(horizontal = SoftSpace.card, vertical = 12.dp)) {
                Text("Ресурсы приложения", style = CobaltType.bodyStrong, color = c.text)
                Spacer(Modifier.height(4.dp))
                Text(
                    when {
                        payload == null -> "Пока не собраны. Строки, которые нельзя перехватить " +
                            "в рантайме, появятся здесь после сборки."
                        patchApplied.contains(key) -> "Применено через Cobalt Platform" +
                            (payload.label.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "")
                        else -> "Требуется Cobalt Platform · ${payload.describe()}"
                    },
                    style = CobaltType.caption,
                    color = c.muted,
                )
                if (payload?.changeSummary?.isNotBlank() == true) {
                    Spacer(Modifier.height(4.dp))
                    Text("Изменения: ${payload.changeSummary}", style = CobaltType.caption, color = c.muted)
                }
            }
            if (payload != null && !patchApplied.contains(key)) {
                val isPatching = patchBusy[key] == true
                SoftDivider(start = 0.dp, end = 0.dp)
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = SoftSpace.card, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SoftAction(
                        text = if (isPatching) (patchStage[key] ?: "Применение…") else "Применить",
                        enabled = !isPatching,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (isPatching) return@SoftAction
                        pendingUpdate = PendingTranslationUpdate(app.packageName, payload, true)
                        checkingInfo = true
                        scope.launch {
                            pendingLog = state?.catalog?.let {
                                UpdateRepository.fetchTranslationChanges(
                                    catalog = it,
                                    packageName = app.packageName,
                                    layerName = "platform",
                                ).getOrNull()
                            }
                            checkingInfo = false
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    SoftTextAction("Информация", enabled = !isPatching) {
                        pendingUpdate = PendingTranslationUpdate(app.packageName, payload, true)
                        checkingInfo = true
                        scope.launch {
                            pendingLog = state?.catalog?.let {
                                UpdateRepository.fetchTranslationChanges(
                                    catalog = it,
                                    packageName = app.packageName,
                                    layerName = "platform",
                                ).getOrNull()
                            }
                            checkingInfo = false
                        }
                    }
                }
                val pp = patchProgress[key]
                if (isPatching && pp != null) LinearProgress(pp)
            }
            if (patchApplied.contains(key)) {
                SoftDivider(start = 0.dp, end = 0.dp)
                SoftRow(
                    title = "Снять подмену",
                    summary = "Вернуть оригинальные ресурсы приложения",
                    showChevron = false,
                    onClick = {
                        scope.launch {
                            val r = PlatformControl.umount(app.packageName)
                            if (r != null) {
                                patchApplied = patchApplied - key
                                message = "Подмена снята"
                            } else message = "Не удалось снять подмену"
                        }
                    },
                )
            }
        }
    }

    if (checkingInfo) {
        SoftNote("Загружаю информацию об обновлении…", Modifier.padding(top = 10.dp))
    }

    pendingUpdate?.let { update ->
        if (!checkingInfo) {
            val log = pendingLog
            UpdateInfoDialog(
                show = true,
                title = if (update.isPlatform) "Обновление ресурсов APK" else "Обновление перевода",
                subtitle = update.layer.changeSummary.ifBlank { "Изменения опубликованы в каталоге Cobalt" },
                mainGroups = log?.toChangeGroups().orEmpty(),
                details = log?.details.orEmpty() + listOf("Файл проверяется по SHA-256 перед установкой"),
                primaryText = "Обновить",
                onDismiss = { pendingUpdate = null },
                onPrimary = {
                    val currentUpdate = update
                    pendingUpdate = null
                    if (currentUpdate.isPlatform) {
                        val key = currentUpdate.packageName
                        patchBusy[key] = true
                        patchProgress[key] = 0f
                        scope.launch {
                            val result = PatchInstaller.apply(
                                context = context,
                                packageName = currentUpdate.packageName,
                                layer = currentUpdate.layer,
                                onProgress = { progressValue ->
                                    patchStage[key] = progressValue.stage
                                    patchProgress[key] = progressValue.fraction
                                },
                            )
                            patchBusy[key] = false
                            patchStage.remove(key)
                            patchProgress.remove(key)
                            result.onSuccess {
                                patchApplied = patchApplied + key
                                message = "Ресурсы применены. Перезапустите приложение."
                                onChanged()
                            }.onFailure { message = it.message ?: "Не удалось применить ресурсы" }
                        }
                    } else {
                        val key = currentUpdate.packageName
                        busy[key] = true
                        progress[key] = 0f
                        scope.launch {
                            val result = DictionaryInstaller.install(context, key, currentUpdate.layer) { progress[key] = it }
                            busy[key] = false
                            progress.remove(key)
                            result.onSuccess {
                                message = "Перевод обновлён: ${it.label.ifBlank { it.contentId }}"
                                reloadInstalled()
                                onChanged()
                            }.onFailure { message = it.message ?: "Не удалось загрузить перевод" }
                        }
                    }
                },
            )
        }
    }

    AnimatedContent(
        targetState = message,
        label = "updateMessage",
        transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(90)) },
        modifier = Modifier.fillMaxWidth(),
    ) { text ->
        if (text == null) Spacer(Modifier.height(0.dp)) else SoftNote(text, Modifier.padding(top = 14.dp))
    }

    SoftNote(
        "Переводы интерфейса загружает и применяет Cobalt App. Ресурсы приложения устанавливаются через Cobalt Platform. " +
            "Проверка поддерживаемых приложений выполняется непосредственно на устройстве — список установленных приложений не передаётся на сервер.",
        Modifier.padding(top = 10.dp),
    )
}

private val KNOWN_PACKAGES = listOf("com.heytap.themestore", "com.oplus.themestore")

private fun InstalledDictionary.describeText(): String =
    buildList {
        add(label.ifBlank { contentId })
        if (entries > 0) add("$entries строк")
    }.joinToString(" · ")

@Composable
private fun LinearProgress(value: Float) {
    val c = LocalDesign.current
    val percent = (value.coerceIn(0f, 1f) * 100).toInt()
    Column(Modifier.fillMaxWidth().padding(horizontal = SoftSpace.card, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Применение", style = CobaltType.caption, color = c.muted, modifier = Modifier.weight(1f))
            Text("$percent%", style = CobaltType.numeric, color = c.accent)
        }
        Spacer(Modifier.height(5.dp))
        Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(c.line)) {
            Box(Modifier.fillMaxWidth(value.coerceIn(0f, 1f)).height(6.dp)
                .clip(CircleShape).background(c.accent))
        }
    }
}
