@file:Suppress("DEPRECATION")
package com.cobalt.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch

class CouiActivity : ComponentActivity() {
    private val appearance by lazy { getSharedPreferences("appearance", 0) }
    private var themeMode by mutableStateOf("system")
    private var uiSize by mutableStateOf("normal")
    private var systemAmoled by mutableStateOf(false)
    private var colorMode by mutableStateOf("app")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        themeMode = appearance.getString("theme_mode", "system") ?: "system"
        systemAmoled = appearance.getBoolean("system_amoled", false)
        colorMode = appearance.getString("color_mode", "app") ?: "app"
        uiSize = appearance.getString("ui_size", "normal") ?: "normal"
        installedPackages = bundledProfiles.map { it.packageName }
            .filter { CobaltProfiles.installed(this, it) != null }
            .toSet()
        val cached = CatalogRepository.cached(this)
        cached.catalog?.let { knownPackages = it.apps.map { app -> app.packageName } }
        setContent {
            DesignTheme(
                activity = this,
                mode = themeMode,
                size = uiSize,
                systemAmoled = systemAmoled,
                colorMode = colorMode,
            ) { Root() }
        }
    }

    private enum class Overlay { PROFILE, UPDATES }

    private val bundledProfiles = listOf(
        SupportedProfile("Theme Store", "com.heytap.themestore"),
        SupportedProfile("Theme Basic", "com.oplus.themestore"),
    )

    private var knownPackages by mutableStateOf<List<String>>(emptyList())
    private var installedPackages by mutableStateOf<Set<String>>(emptySet())

    private val supportedProfiles: List<SupportedProfile>
        get() = bundledProfiles.filter { profile ->
            profile.packageName in installedPackages &&
                (knownPackages.isEmpty() || profile.packageName in knownPackages)
        }

    private fun purposeOf(packageName: String): String = when (packageName) {
        "com.heytap.themestore" -> "Магазин тем, обоев и шрифтов"
        "com.oplus.themestore" -> "Магазин оформления для OnePlus"
        else -> "Поддерживаемое приложение"
    }

    @Composable
    private fun Root() {
        val c = LocalDesign.current
        val local = remember { getSharedPreferences("local", 0) }
        val pagerState = rememberPagerState(initialPage = 0, pageCount = { 4 })
        val scope = rememberCoroutineScope()

        var overlay by rememberSaveable(
            stateSaver = Saver<Overlay?, String>(
                save = { it?.name ?: "" },
                restore = { name -> if (name.isEmpty()) null else Overlay.valueOf(name) },
            ),
        ) { mutableStateOf<Overlay?>(null) }

        var selectedPackage by rememberSaveable { mutableStateOf(ThemeStoreProfile.packageName) }
        var updateFocusPackage by rememberSaveable { mutableStateOf<String?>(null) }
        val selected = supportedProfiles.firstOrNull { it.packageName == selectedPackage } ?: ThemeStoreProfile
        var dictionaryVersion by remember { mutableIntStateOf(0) }
        var statusRefresh by remember { mutableIntStateOf(0) }
        var runtimeStatus by remember { mutableStateOf(CobaltRuntimeStatus.loading()) }
        val searchState = rememberTextFieldState()

        val templates = remember {
            mutableStateMapOf<String, String>().apply {
                supportedProfiles.forEach { put(it.packageName, local.getString("template_${it.packageName}", "none") ?: "none") }
            }
        }

        BackHandler(enabled = overlay != null) { overlay = null }

        LaunchedEffect(Unit) {
            val refreshed = CatalogRepository.refresh(this@CouiActivity)
            if (refreshed.catalog != null) knownPackages = refreshed.apps.map { it.packageName }
        }

        LaunchedEffect(statusRefresh) {
            runtimeStatus = CobaltRuntimeChecker.check(this@CouiActivity)
        }

        val owner = LocalLifecycleOwner.current
        DisposableEffect(owner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    statusRefresh++
                    dictionaryVersion++
                    supportedProfiles.forEach {
                        templates[it.packageName] = local.getString("template_${it.packageName}", "none") ?: "none"
                    }
                }
            }
            owner.lifecycle.addObserver(observer)
            onDispose { owner.lifecycle.removeObserver(observer) }
        }

        val templateOf: (String) -> String = { templates[it] ?: "none" }
        val chapters = listOf(
            "Главная",
            "Приложения",
            "Настройки",
            "О проекте",
        )

        CompositionLocalProvider(LocalBarInset provides 24.dp) {
            Column(
                Modifier
                    .fillMaxSize()
                    .background(c.background)
                    .safeDrawingPadding()
                    .padding(start = 0.dp, end = 8.dp, top = 6.dp, bottom = 8.dp),
            ) {
                BookDeck(Modifier.weight(1f).fillMaxWidth()) {
                    HorizontalPager(
                        state = pagerState,
                        beyondViewportPageCount = 1,
                        modifier = Modifier.fillMaxSize(),
                    ) { pageIndex ->
                        val pageOffset = (pageIndex - pagerState.currentPage) - pagerState.currentPageOffsetFraction
                        BookSheet(
                            modifier = Modifier.fillMaxSize(),
                            pageOffset = pageOffset,
                        ) {
                            BookRunningHeader(
                                chapter = chapters[pageIndex],
                                pageNumber = pageIndex + 1,
                                totalPages = chapters.size,
                            )
                            when (pageIndex) {
                                0 -> HomeScreen(
                                    onOpenApps = { scope.launch { pagerState.animateScrollToPage(1) } },
                                    onOpenSettings = { scope.launch { pagerState.animateScrollToPage(2) } },
                                    onOpenLogs = { openLogs() },
                                    status = runtimeStatus,
                                )
                                1 -> AppsScreen(
                                    searchState = searchState,
                                    templateOf = templateOf,
                                    onOpenApp = { profile ->
                                        selectedPackage = profile.packageName
                                        overlay = Overlay.PROFILE
                                    },
                                )
                                2 -> SettingsScreen(
                                    themeMode = themeMode,
                                    colorMode = colorMode,
                                    uiSize = uiSize,
                                    systemAmoled = systemAmoled,
                                    onTheme = { themeMode = it; appearance.edit { putString("theme_mode", it) } },
                                    onColors = { colorMode = it; appearance.edit { putString("color_mode", it) } },
                                    onSize = { uiSize = it; appearance.edit { putString("ui_size", it) } },
                                    onSystemAmoled = { systemAmoled = it; appearance.edit { putBoolean("system_amoled", it) } },
                                    onOpenUpdates = { updateFocusPackage = null; overlay = Overlay.UPDATES },
                                    onUpdatesApplied = { dictionaryVersion++ },
                                )
                                3 -> AboutScreen(version = versionName())
                            }
                        }
                    }

                    val ease = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
                    AnimatedContent(
                        targetState = overlay,
                        label = "bookOverlay",
                        transitionSpec = {
                            (fadeIn(tween(220, easing = ease)) +
                                slideInHorizontally(tween(300, easing = ease)) { it })
                                .togetherWith(
                                    fadeOut(tween(180, easing = ease)) +
                                        slideOutHorizontally(tween(260, easing = ease)) { it },
                                )
                        },
                    ) { activeOverlay ->
                        when (activeOverlay) {
                            Overlay.PROFILE -> BookSheet(
                                modifier = Modifier.fillMaxSize(),
                                pageOffset = 0f,
                            ) {
                                BookRunningHeader(
                                    chapter = selected.label,
                                    pageNumber = 1,
                                    totalPages = 1,
                                    onBack = { overlay = null },
                                    trailing = { ProfileRestartAction(selected) },
                                )
                                ProfileScreen(
                                    profile = selected,
                                    template = templateOf(selected.packageName),
                                    onTemplate = { value ->
                                        templates[selected.packageName] = value
                                        local.edit(commit = true) { putString("template_${selected.packageName}", value) }
                                        scope.launch {
                                            val identity = CobaltProfiles.installed(this@CouiActivity, selected.packageName)
                                            if (identity != null) {
                                                PlatformControl.configure(
                                                    context = this@CouiActivity,
                                                    packageName = selected.packageName,
                                                    versionName = identity.versionName,
                                                    versionCode = identity.versionCode,
                                                    features = if (value == "none") emptySet() else setOf("base"),
                                                )
                                            }
                                        }
                                        Toast.makeText(
                                            this@CouiActivity,
                                            if (value == "none") "Перевод для ${selected.label} выключен"
                                            else "Шаблон включён. Перезапустите ${selected.label}",
                                            Toast.LENGTH_LONG,
                                        ).show()
                                    },
                                    onDictionary = { openEditor() },
                                    onOpenUpdates = { updateFocusPackage = selected.packageName; overlay = Overlay.UPDATES },
                                )
                            }
                            Overlay.UPDATES -> BookSheet(
                                modifier = Modifier.fillMaxSize(),
                                pageOffset = 0f,
                            ) {
                                BookRunningHeader(
                                    chapter = "Обновления",
                                    pageNumber = 1,
                                    totalPages = 1,
                                    onBack = { overlay = null; updateFocusPackage = null },
                                )
                                BookLeaf {
                                    UpdatesContent(
                                        context = this@CouiActivity,
                                        focusPackage = updateFocusPackage,
                                        onBack = { overlay = null; updateFocusPackage = null },
                                        onChanged = { dictionaryVersion++ },
                                    )
                                }
                            }
                            null -> Spacer(Modifier.size(0.dp))
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                BookRibbonBar(
                    chapters = chapters,
                    selected = pagerState.currentPage,
                ) { targetPage ->
                    overlay = null
                    scope.launch { pagerState.animateScrollToPage(targetPage) }
                }
            }
        }
    }

    private fun versionName(): String = ReleaseInfo.DISPLAY

    private fun shortVersion(): String {
        val full = versionName()
        val short = full.substringBefore('-')
        return if (short.isBlank()) full else "v$short"
    }

    private fun openEditor() {
        startActivity(Intent(this, MainActivity::class.java))
        overridePendingTransition(R.anim.cobalt_enter_right, R.anim.cobalt_exit_left)
    }

    private fun openLogs() {
        startActivity(Intent(this, LogsActivity::class.java))
        overridePendingTransition(R.anim.cobalt_enter_right, R.anim.cobalt_exit_left)
    }

    @Composable
    private fun BookLeaf(content: @Composable ColumnScope.() -> Unit) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 8.dp, bottom = 24.dp),
            content = content,
        )
    }

    @Composable
    private fun AppIcon(packageName: String, size: Int = 44) {
        val bitmap = remember(packageName) {
            runCatching { packageManager.getApplicationIcon(packageName).toBitmap(96, 96).asImageBitmap() }.getOrNull()
        }
        if (bitmap != null) {
            Image(bitmap, null, Modifier.size(size.dp).clip(SoftShape.control))
        } else {
            val c = LocalDesign.current
            Box(
                Modifier.size(size.dp).clip(SoftShape.control).background(c.well),
                contentAlignment = Alignment.Center,
            ) {
                Icon(ImageVector.vectorResource(R.drawable.ic_phosphor_translate), null, tint = c.muted, modifier = Modifier.size((size * .46).dp))
            }
        }
    }

    @Composable
    private fun PackageLine(packageName: String) {
        Text(
            breakableId(packageName),
            style = CobaltType.monoSmall,
            color = LocalDesign.current.faint,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }

    @Composable
    private fun AppCard(profile: SupportedProfile, template: String, onClick: () -> Unit) {
        val c = LocalDesign.current
        val enabled = template != "none"
        SoftCard(onClick = onClick) {
            Row(verticalAlignment = Alignment.Top) {
                AppIcon(profile.packageName)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        profile.label,
                        style = CobaltType.headline,
                        color = c.text,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        purposeOf(profile.packageName),
                        style = CobaltType.caption,
                        color = c.muted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    PackageLine(profile.packageName)
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SoftBadge(
                    tone = if (enabled) SoftTone.POSITIVE else SoftTone.NEUTRAL,
                    text = if (enabled) "Русский" else "Отключён",
                )
                Spacer(Modifier.weight(1f))
                Text("Открыть", style = CobaltType.micro, color = c.accent)
                Icon(
                    ImageVector.vectorResource(R.drawable.ic_phosphor_chevron), null,
                    tint = c.accent, modifier = Modifier.padding(start = 4.dp).size(13.dp),
                )
            }
        }
    }

    @Composable
    private fun HomeScreen(
        onOpenApps: () -> Unit,
        onOpenSettings: () -> Unit,
        onOpenLogs: () -> Unit,
        status: CobaltRuntimeStatus,
    ) {
        val c = LocalDesign.current
        BookLeaf {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text("Cobalt", style = CobaltType.display, color = c.text)
                    Text(
                        "ColorOS — на вашем языке",
                        style = CobaltType.caption,
                        color = c.muted,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                Text(shortVersion(), style = CobaltType.monoSmall, color = c.faint, modifier = Modifier.padding(bottom = 5.dp))
            }

            SoftSectionTitle("Состояние компонентов")
            StatusCard(status)
            Spacer(Modifier.height(SoftSpace.group))
            SoftGroup {
                SoftRow("Диагностика", "Журнал системных операций", onClick = onOpenLogs)
                SoftDivider()
                SoftRow("Приложения", "Профили и языковые слои", onClick = onOpenApps)
                SoftDivider()
                SoftRow("Настройки", "Тема, акцент, текст и обновления", onClick = onOpenSettings)
            }

            SoftNote(
                "Cobalt — независимый проект, не связанный с OPPO, OnePlus и Realme.",
                Modifier.padding(top = 20.dp),
            )
        }
    }

    @Composable
    private fun StatusCard(status: CobaltRuntimeStatus) {
        val c = LocalDesign.current
        val active = listOf(status.root, status.lsposed, status.platform).count { it == CobaltCheckState.ACTIVE }
        val summary = when {
            active == 3 -> "Компоненты подключены"
            status.root != CobaltCheckState.ACTIVE -> "Root-доступ не обнаружен"
            status.lsposed != CobaltCheckState.ACTIVE -> "LSPosed не активен"
            else -> "Cobalt Platform не подключена"
        }
        val detail = when {
            active == 3 -> "Компоненты Cobalt готовы к работе."
            status.root != CobaltCheckState.ACTIVE -> "Для системных операций требуется root-доступ."
            status.lsposed != CobaltCheckState.ACTIVE -> "Включите модуль Cobalt в LSPosed и добавьте приложения в область действия."
            else -> "Подключите Cobalt Platform для системных операций."
        }
        SoftCard(tone = CardTone.RAISED) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(summary, style = CobaltType.title, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(detail, style = CobaltType.caption, color = c.muted, modifier = Modifier.padding(top = 6.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    "$active / 3",
                    style = CobaltType.numeric,
                    color = if (active == 3) c.success else c.warning,
                )
            }
            Spacer(Modifier.height(16.dp))
            SoftWell(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)) {
                SoftStatusLine("Root", status.root.label("root"), status.root.tone())
                SoftStatusLine("LSPosed", status.lsposed.label("lsposed"), status.lsposed.tone())
                SoftStatusLine("Cobalt Platform", status.platform.label("platform"), status.platform.tone())
            }
        }
    }

    @Composable
    private fun AppsScreen(
        searchState: TextFieldState,
        templateOf: (String) -> String,
        onOpenApp: (SupportedProfile) -> Unit,
    ) {
        val c = LocalDesign.current
        val query = searchState.text.toString()
        BookLeaf {
            SoftPageTitle(title = "Приложения", subtitle = "Поддерживаемые приложения ColorOS")
            Spacer(Modifier.height(18.dp))
            SoftSearchField(searchState, "Поиск приложений")

            val apps = supportedProfiles.filter {
                query.isBlank() || it.label.contains(query, true) || it.packageName.contains(query, true)
            }
            Spacer(Modifier.height(14.dp))
            Text(
                if (apps.isEmpty()) "Ничего не найдено" else "Найдено: ${apps.size}",
                style = CobaltType.label,
                color = c.faint,
            )
            Spacer(Modifier.height(10.dp))

            if (apps.isEmpty()) {
                SoftEmptyState(
                    title = "Ничего не найдено",
                    text = "Проверьте название или имя пакета.",
                    actionText = "Сбросить поиск",
                ) { searchState.clearText() }
            } else {
                apps.forEachIndexed { index, profile ->
                    if (index > 0) Spacer(Modifier.height(SoftSpace.group))
                    AppCard(profile, templateOf(profile.packageName)) { onOpenApp(profile) }
                }
            }

            SoftNote(
                "Настройки сохраняются отдельно для каждого приложения и его версии.",
                Modifier.padding(top = 18.dp),
            )
        }
    }

    @Composable
    private fun AboutScreen(version: String) {
        val c = LocalDesign.current
        BookLeaf {
            SoftPageTitle(title = "О проекте", eyebrow = "Cobalt")

            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.cobalt_logo), null, Modifier.size(52.dp).clip(SoftShape.control))
                Spacer(Modifier.width(14.dp))
                Text(
                    "Инструменты локализации и адаптации интерфейса приложений ColorOS",
                    style = CobaltType.caption,
                    color = c.muted,
                )
            }

            SoftSectionTitle("Сборка")
            SoftGroup {
                Column(Modifier.padding(horizontal = SoftSpace.card, vertical = 4.dp)) {
                    SoftKeyValue("Версия", version)
                    SoftDivider(start = 0.dp, end = 0.dp)
                    SoftKeyValue("Пакет", "com.cobalt.app", mono = true)
                    SoftDivider(start = 0.dp, end = 0.dp)
                    SoftKeyValue("Модуль", "LSPosed")
                }
            }

            SoftSectionTitle("Возможности")
            SoftCard {
                Text(
                    "Cobalt изменяет интерфейс поддерживаемых приложений ColorOS. Сейчас доступны перевод на русский и собственные словари.",
                    style = CobaltType.body,
                    color = c.text,
                )
                Text(
                    "У каждого приложения свой профиль: настройки зависят от пакета и версии.",
                    style = CobaltType.caption,
                    color = c.muted,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }

            SoftSectionTitle("Ограничения")
            SoftCard {
                Text(
                    "Cobalt находится на этапе бета-тестирования. Часть изменений может применяться некорректно или не затрагивать отдельные элементы интерфейса. Совместимость постепенно улучшается.",
                    style = CobaltType.caption,
                    color = c.muted,
                )
                Text(
                    "Для применения изменений необходимо перезапустить приложение. Для работы требуется LSPosed.",
                    style = CobaltType.caption,
                    color = c.muted,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            SoftSectionTitle("Автор проекта")
            SoftGroup {
                SoftRow("Telegram", "@elqaren", onClick = { open("https://t.me/elqaren") })
                SoftDivider()
                SoftRow("GitHub", "github.com/phontex", onClick = { open("https://github.com/phontex") })
            }

            SoftNote("Cobalt — независимый проект, не связанный с OPPO, OnePlus и Realme.", Modifier.padding(top = 22.dp))
        }
    }

    private fun open(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    }

    @Composable
    private fun ProfileScreen(
        profile: SupportedProfile,
        template: String,
        onTemplate: (String) -> Unit,
        onDictionary: () -> Unit,
        onOpenUpdates: () -> Unit,
    ) {
        val c = LocalDesign.current
        BookLeaf {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIcon(profile.packageName, 52)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        profile.label,
                        style = CobaltType.title,
                        color = c.text,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    PackageLine(profile.packageName)
                }
            }
            Text(
                purposeOf(profile.packageName),
                style = CobaltType.body,
                color = c.muted,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )

            val installedVersion = remember(profile.packageName) { CobaltProfiles.installed(this@CouiActivity, profile.packageName)?.versionName.orEmpty() }
            var compatibility by remember(profile.packageName) { mutableStateOf<Compatibility?>(null) }
            LaunchedEffect(profile.packageName) {
                compatibility = CatalogRepository.cachedCompatibility(this@CouiActivity, profile.packageName)
            }
            if (installedVersion.isNotBlank()) {
                SoftSectionTitle("Версия")
                SoftCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Установлена", style = CobaltType.bodyStrong, color = c.text)
                        Spacer(Modifier.weight(1f))
                        Text(installedVersion, style = CobaltType.body, color = c.muted)
                    }
                    val notice = compatibility?.versionNotice(installedVersion)
                    Text(
                        notice?.text ?: "Данные о поддерживаемых версиях появятся после проверки обновлений. Cobalt продолжает работать.",
                        style = CobaltType.caption,
                        color = if (notice?.warning == true) c.text else c.muted,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }

            SoftSectionTitle("Перевод")
            SoftCard {
                SoftSegmented(
                    labels = listOf("Отключён", "Русский"),
                    selectedIndex = if (template == "ru") 1 else 0,
                ) { onTemplate(if (it == 1) "ru" else "none") }
                Text(
                    if (template == "none") "Cobalt ничего не меняет в этом приложении."
                    else "Используется встроенный перевод. Ваши собственные переводы имеют приоритет.",
                    style = CobaltType.caption,
                    color = c.muted,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }

            SoftSectionTitle("Мои переводы")
            SoftGroup {
                SoftRow("Собственные переводы", "Просмотр, импорт и экспорт переводов", onClick = onDictionary)
            }

            SoftSectionTitle("Обновления")
            SoftGroup {
                SoftRow(
                    "Проверить наличие обновлений",
                    "Новые переводы и ресурсы для этого приложения",
                    onClick = onOpenUpdates,
                )
                SoftDivider()
                SoftRow(
                    "История изменений",
                    "Список изменений будет доступен перед установкой",
                    showChevron = false,
                    onClick = onOpenUpdates,
                )
            }

            SoftSectionTitle("LSPosed")
            SoftCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("LSPosed", style = CobaltType.bodyStrong, color = c.text)
                    Spacer(Modifier.weight(1f))
                    SoftBadge(SoftTone.NEUTRAL, "Область действия")
                }
                Text(
                    "Добавьте это приложение в область действия модуля Cobalt в LSPosed. После изменения настроек перезапустите приложение.",
                    style = CobaltType.caption,
                    color = c.muted,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            SoftNote("Профиль приложения выбирается по имени пакета и версии.", Modifier.padding(top = 20.dp))
        }
    }

    @Composable
    private fun SettingsScreen(
        themeMode: String,
        colorMode: String,
        uiSize: String,
        systemAmoled: Boolean,
        onTheme: (String) -> Unit,
        onColors: (String) -> Unit,
        onSize: (String) -> Unit,
        onSystemAmoled: (Boolean) -> Unit,
        onOpenUpdates: () -> Unit,
        onUpdatesApplied: () -> Unit,
    ) {
        val c = LocalDesign.current

        BookLeaf {
            SoftPageTitle(title = "Настройки", subtitle = "Тема, цвета, текст и обновления")

            SoftSectionTitle("Тема")
            SoftGroup {
                Column(Modifier.padding(SoftSpace.card)) {
                    SoftChoiceGroup(
                        title = "Тема оформления",
                        labels = listOf("Как в системе", "Светлая", "Тёмная", "AMOLED"),
                        selectedIndex = listOf("system", "light", "dark", "amoled").indexOf(themeMode).coerceAtLeast(0),
                        onChange = { onTheme(listOf("system", "light", "dark", "amoled")[it]) },
                    )
                }
                if (themeMode == "system") {
                    SoftDivider(start = 0.dp, end = 0.dp)
                    SoftToggleRow("AMOLED в тёмной теме", "Чёрный фон вместо тёмно-серого", systemAmoled, onSystemAmoled)
                }
            }

            SoftSectionTitle("Акцент")
            SoftGroup {
                Column(Modifier.padding(SoftSpace.card)) {
                    SoftChoiceGroup(
                        title = "Цветовая тема",
                        labels = listOf("Cobalt", "Обои", "Пастель"),
                        selectedIndex = listOf("app", "wallpaper", "pastel").indexOf(colorMode).coerceAtLeast(0),
                        onChange = { index ->
                            val value = listOf("app", "wallpaper", "pastel")[index]
                            if (value == "wallpaper" && Build.VERSION.SDK_INT < 31) {
                                Toast.makeText(this@CouiActivity, "Цвета обоев доступны на Android 12 и новее", Toast.LENGTH_LONG).show()
                            } else onColors(value)
                        },
                    )
                    Text(
                        "Цвета обоев подстраиваются под системный фон, если устройство это поддерживает.",
                        style = CobaltType.caption,
                        color = c.muted,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }

            SoftSectionTitle("Текст")
            SoftGroup {
                Column(Modifier.padding(SoftSpace.card)) {
                    SoftChoiceGroup(
                        title = "Размер текста",
                        labels = listOf("Компактный", "Обычный", "Крупный"),
                        selectedIndex = listOf("compact", "normal", "large").indexOf(uiSize).coerceAtLeast(0),
                        onChange = { onSize(listOf("compact", "normal", "large")[it]) },
                    )
                    Spacer(Modifier.height(14.dp))
                    SoftWell {
                        Text("ColorOS на вашем языке", style = CobaltType.bodyStrong, color = c.text)
                        Text(
                            "Пример отображения текста в приложении.",
                            style = CobaltType.caption,
                            color = c.muted,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }

            val updateScope = rememberCoroutineScope()
            var appUpdateState by remember { mutableStateOf<AppUpdateState>(AppUpdateState.Idle) }
            var catalogChanges by remember { mutableStateOf<TranslationChangeLog?>(null) }
            var latestCatalog by remember { mutableStateOf<Catalog?>(null) }
            var catalogChecking by remember { mutableStateOf(false) }
            var catalogApplying by remember { mutableStateOf(false) }

            SoftSectionTitle("Обновления")
            SoftGroup {
                val checkCobaltUpdate = {
                    appUpdateState = AppUpdateState.Checking
                    updateScope.launch {
                        appUpdateState = UpdateRepository.checkApp(this@CouiActivity).getOrElse {
                            AppUpdateState.Failed(it.message ?: "Не удалось проверить обновления")
                        }
                    }
                    Unit
                }
                SoftRow(
                    "Обновление приложения",
                    when (val state = appUpdateState) {
                        AppUpdateState.Checking -> "Проверка последней версии…"
                        is AppUpdateState.Available -> "Доступна версия ${state.update.version}"
                        is AppUpdateState.Current -> "Установлена последняя версия"
                        is AppUpdateState.Failed -> state.message
                        AppUpdateState.Idle -> "Проверить наличие новой версии Cobalt"
                    },
                    onClick = checkCobaltUpdate,
                )
                SoftDivider()
                SoftRow(
                    "История изменений",
                    "Список изменений текущей версии",
                    showChevron = false,
                    onClick = checkCobaltUpdate,
                )
                SoftDivider()
                SoftRow(
                    "Обновления переводов",
                    if (catalogChecking) "Получение последнего журнала…" else "Проверить наличие новых переводов",
                    onClick = {
                        catalogChecking = true
                        updateScope.launch {
                            val refreshed = CatalogRepository.refresh(this@CouiActivity)
                            latestCatalog = refreshed.catalog
                            catalogChanges = refreshed.catalog?.let { UpdateRepository.fetchTranslationChanges(it).getOrNull() }
                            catalogChecking = false
                            if (catalogChanges == null) onOpenUpdates()
                        }
                    },
                )
                SoftDivider()
                SoftRow(
                    "История изменений переводов",
                    "Последние изменения словарей и ресурсов из GitHub",
                    showChevron = false,
                    onClick = {
                        catalogChecking = true
                        updateScope.launch {
                            val refreshed = CatalogRepository.refresh(this@CouiActivity)
                            latestCatalog = refreshed.catalog
                            catalogChanges = refreshed.catalog?.let { UpdateRepository.fetchTranslationChanges(it).getOrNull() }
                            catalogChecking = false
                        }
                    },
                )
            }

            val availableUpdate = (appUpdateState as? AppUpdateState.Available)?.update
            val currentUpdate = (appUpdateState as? AppUpdateState.Current)?.latest
            val appInfoUpdate = availableUpdate ?: currentUpdate
            if (appInfoUpdate != null) {
                UpdateInfoDialog(
                    show = true,
                    title = if (availableUpdate != null) "Доступно обновление Cobalt" else "Информация об обновлении Cobalt",
                    subtitle = if (availableUpdate != null) "${appInfoUpdate.title} · ${appInfoUpdate.version}"
                    else "Установлена последняя версия · ${appInfoUpdate.version}",
                    mainGroups = appInfoUpdate.toChangeGroups(),
                    details = appInfoUpdate.details,
                    primaryText = if (availableUpdate != null) "Обновить" else "Закрыть",
                    onDismiss = { appUpdateState = AppUpdateState.Idle },
                    onPrimary = {
                        if (availableUpdate == null) {
                            appUpdateState = AppUpdateState.Idle
                        } else if (!packageManager.canRequestPackageInstalls()) {
                            startActivity(
                                Intent(
                                    android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                    "package:$packageName".toUri(),
                                ),
                            )
                            Toast.makeText(
                                this@CouiActivity,
                                "Разрешите установку обновлений для Cobalt и повторите действие",
                                Toast.LENGTH_LONG,
                            ).show()
                        } else {
                            updateScope.launch {
                                val intent = UpdateRepository.downloadApp(this@CouiActivity, availableUpdate).getOrElse {
                                    Toast.makeText(this@CouiActivity, it.message ?: "Не удалось скачать APK", Toast.LENGTH_LONG).show()
                                    null
                                }
                                if (intent != null) startActivity(intent)
                                appUpdateState = AppUpdateState.Idle
                            }
                        }
                    },
                )
            }
            catalogChanges?.let { changes ->
                UpdateInfoDialog(
                    show = true,
                    title = "Обновления переводов",
                    subtitle = "Последнее изменение каталога с GitHub",
                    mainGroups = changes.toChangeGroups(),
                    details = changes.details,
                    primaryText = "Обновить",
                    onDismiss = { catalogChanges = null },
                    onPrimary = {
                        catalogChanges = null
                        val catalog = latestCatalog
                        if (catalog == null) {
                            onOpenUpdates()
                        } else {
                            catalogApplying = true
                            updateScope.launch {
                                var runtimeCount = 0
                                var resourceCount = 0
                                catalog.apps
                                    .filter { CobaltProfiles.installed(this@CouiActivity, it.packageName) != null }
                                    .forEach { app ->
                                        app.bestRuntime?.let { layer ->
                                            if (DictionaryInstaller.install(this@CouiActivity, app.packageName, layer).isSuccess) runtimeCount++
                                        }
                                        app.bestPayload?.let { layer ->
                                            if (PatchInstaller.apply(this@CouiActivity, app.packageName, layer).isSuccess) resourceCount++
                                        }
                                    }
                                catalogApplying = false
                                Toast.makeText(
                                    this@CouiActivity,
                                    "Обновлено: языковые слои $runtimeCount, ресурсные слои $resourceCount",
                                    Toast.LENGTH_LONG,
                                ).show()
                                onUpdatesApplied()
                            }
                        }
                    },
                )
            }
            if (catalogApplying) {
                SoftNote("Применяю обновления языков…", Modifier.padding(top = 10.dp))
            }

            SoftNote(
                "Настройки применяются сразу. Доступность отдельных возможностей зависит от версии Android.",
                Modifier.padding(top = 20.dp),
            )
        }
    }

    private fun CobaltCheckState.label(kind: String): String = when (this) {
        CobaltCheckState.ACTIVE -> if (kind == "platform") "Подключена" else "Активен"
        CobaltCheckState.INACTIVE -> "Не обнаружен"
        CobaltCheckState.UNKNOWN -> "Не активен"
        CobaltCheckState.CHECKING -> "Проверка…"
    }

    private fun CobaltCheckState.tone(): SoftTone = when (this) {
        CobaltCheckState.ACTIVE -> SoftTone.POSITIVE
        CobaltCheckState.INACTIVE -> SoftTone.NEUTRAL
        CobaltCheckState.UNKNOWN -> SoftTone.WARNING
        CobaltCheckState.CHECKING -> SoftTone.NEUTRAL
    }
}
