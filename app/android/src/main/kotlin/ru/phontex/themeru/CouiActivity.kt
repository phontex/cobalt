package ru.phontex.themeru

import androidx.core.content.edit
import androidx.core.net.toUri

import android.content.Intent
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.CubicBezierEasing
import kotlinx.coroutines.launch
import com.mi.onextbox.ui.common.AppIcons
import com.mi.onextbox.ui.settings.ColorOsWindowDropdownPreference
import io.github.suqi8.coui.kmp.basic.DropdownDefaults
import androidx.compose.foundation.text.input.rememberTextFieldState
import com.mi.onextbox.ui.screens.FeatureSearchBar
import com.mi.onextbox.ui.common.ColorOsSwitch
import io.github.suqi8.coui.kmp.basic.BasicComponent
import io.github.suqi8.coui.kmp.basic.Card as CouiCard
import io.github.suqi8.coui.kmp.basic.CardDefaults
import androidx.compose.ui.semantics.Role
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Lifecycle
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import io.github.suqi8.coui.kmp.basic.SearchBar as CouiSearchBar
import io.github.suqi8.coui.kmp.basic.InputField as CouiInputField
import io.github.suqi8.coui.kmp.basic.Switch as CouiSwitch
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.absoluteValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.mi.onextbox.ui.common.BottomTab
import com.mi.onextbox.ui.layout.ColorOs17BottomNavigationBar
import com.mi.onextbox.ui.layout.LiquidGlassBottomNavigationBar
import io.github.suqi8.coui.kmp.basic.NavigationItem
import ru.phontex.themestoretranslate.R

class CouiActivity : ComponentActivity() {
 private val appearance by lazy { getSharedPreferences("appearance",0) }
 private var themeMode by mutableStateOf("system")
 private var uiSize by mutableStateOf("normal")
 private var systemAmoled by mutableStateOf(false)
 private var hideDividers by mutableStateOf(false)
 private var colorMode by mutableStateOf("app")
 override fun onCreate(savedInstanceState:Bundle?) { super.onCreate(savedInstanceState); enableEdgeToEdge()
  themeMode=appearance.getString("theme_mode","system") ?: "system"
  systemAmoled=appearance.getBoolean("system_amoled",false)
  hideDividers=appearance.getBoolean("hide_dividers",false)
  colorMode=appearance.getString("color_mode",if(appearance.getBoolean("pastel_experiment",false)) "pastel" else "app") ?: "app"
  uiSize=appearance.getString("ui_size","normal") ?: "normal"
  setContent { DesignTheme(this,themeMode,uiSize,systemAmoled,colorMode) { ApplicationScreen() } }
 }
 @Composable private fun ApplicationScreen() {
  val c=LocalDesign.current
  val local=remember { getSharedPreferences("local",0) }
  var translationTemplate by remember { mutableStateOf(local.getString("theme_store_template","none") ?: "none") }
  var detail by remember { mutableStateOf(false) }
  var settings by remember { mutableStateOf(false) }
  var glass by remember { mutableStateOf(appearance.getBoolean("glass",false) && Build.VERSION.SDK_INT>=33) }
  var iosStyle by remember { mutableStateOf(appearance.getString("ui_style","oppo")=="ios") }
  var hideLabels by remember { mutableStateOf(appearance.getBoolean("hide_labels",false)) }
  var profileTitleAlignment by remember { mutableStateOf(appearance.getString("profile_title_alignment","left") ?: "left") }
  var showProfileIcon by remember { mutableStateOf(appearance.getBoolean("profile_show_icon",true)) }
  val searchState=rememberTextFieldState()
  val query=searchState.text.toString()
  val backdrop=rememberLayerBackdrop()
  val pager=rememberPagerState(pageCount={3})
  val scope=rememberCoroutineScope()
  val tab=pager.settledPage
  val focusManager=LocalFocusManager.current
  LaunchedEffect(pager.settledPage) { if(pager.settledPage!=1) focusManager.clearFocus() }
  BackHandler(enabled=detail||settings) { if(settings) settings=false else detail=false }
  Box(Modifier.fillMaxSize().background(c.background).safeDrawingPadding()) {
   HorizontalPager(state=pager,modifier=Modifier.fillMaxSize().then(if(glass && Build.VERSION.SDK_INT>=33) Modifier.layerBackdrop(backdrop) else Modifier),userScrollEnabled=!detail && !settings) { page ->
   AnimatedContent(targetState=if(settings) 2 else if(detail) 1 else 0, label="pageTransition", transitionSpec={
    // COUI-style page motion: the new surface follows the finger with a small lift;
    // the old surface leaves smoothly instead of disappearing in one frame.
    val ease=CubicBezierEasing(0.22f,0.0f,0.0f,1.0f)
    val forward=targetState>initialState
    val entering = slideInHorizontally(tween(360,easing=ease)) { if(forward) it/7 else -it/7 } +
      fadeIn(tween(250)) + scaleIn(initialScale=.965f, animationSpec=tween(360,easing=ease))
    val leaving = slideOutHorizontally(tween(320,easing=ease)) { if(forward) -it/8 else it/8 } +
      fadeOut(tween(220)) + scaleOut(targetScale=.985f, animationSpec=tween(320,easing=ease))
    entering togetherWith leaving
   }) { route ->
   Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start=18.dp,end=18.dp,top=if(route==0) 20.dp else 4.dp,bottom=112.dp)) {
    if(route==2) {
     PageHeader("Настройки") { settings=false }
     Section("ВНЕШНИЙ ВИД")
     SurfaceCard {
      ChoiceRow("Стиль интерфейса",listOf("OPPO ColorOS", "iOS"),listOf("oppo","ios"),if(iosStyle) "ios" else "oppo") { iosStyle=it=="ios";appearance.edit { putString("ui_style",it) } }
      Line()
      ChoiceRow("Цвета",listOf("Цвета Cobalt","Цвета обоев","Пастельные"),listOf("app","wallpaper","pastel"),colorMode) { if(it=="wallpaper" && Build.VERSION.SDK_INT<31) Toast.makeText(this@CouiActivity,"Цвета обоев доступны на Android 12 и новее",Toast.LENGTH_LONG).show() else { colorMode=it;appearance.edit { putString("color_mode",it) } } }
      Line()
      ChoiceRow("Тема",listOf("Как в системе","Светлая","Тёмная","AMOLED"),listOf("system","light","dark","amoled"),themeMode) { themeMode=it;appearance.edit { putString("theme_mode",it) } }
      if(themeMode=="system") { Line(); ToggleRow("AMOLED при тёмной теме","Использовать чёрный фон при смене системной темы",systemAmoled,iosStyle) { systemAmoled=it;appearance.edit { putBoolean("system_amoled",it) } } }
      Line()
      ChoiceRow("Размер текста",listOf("Компактный","Обычный","Крупный"),listOf("compact","normal","large"),uiSize) { uiSize=it;appearance.edit { putString("ui_size",it) } }
      Line()
      ToggleRow("Скрыть разделители","Не показывать линии между пунктами",hideDividers,iosStyle) { hideDividers=it;appearance.edit { putBoolean("hide_dividers",it) } }
     }
     if(iosStyle) Text("Стиль iOS находится в разработке: изменяются переключатели и нижняя панель.",color=c.muted,fontSize=13.sp,lineHeight=18.sp,modifier=Modifier.padding(horizontal=14.dp,vertical=10.dp))
     if(iosStyle) Text("В режиме iOS используется облегчённая панель без Liquid Glass.",color=c.muted,fontSize=13.sp,modifier=Modifier.padding(start=14.dp,end=14.dp,bottom=4.dp))
     Section("ЗАГОЛОВОК ПРОФИЛЯ")
     SurfaceCard {
      ChoiceRow("Положение заголовка",listOf("Слева","По центру"),listOf("left","center"),profileTitleAlignment) { profileTitleAlignment=it;appearance.edit { putString("profile_title_alignment",it) } }
      Line()
      ToggleRow("Значок приложения","Показывать рядом с названием",showProfileIcon,iosStyle) { showProfileIcon=it;appearance.edit { putBoolean("profile_show_icon",it) } }
     }
     Section("НИЖНЯЯ ПАНЕЛЬ")
     SurfaceCard {
      if(!iosStyle) { ToggleRow("Liquid Glass","Стеклянная панель с размытием. Доступна на Android 13 и более новых версиях.",glass,iosStyle) {
       if(it && Build.VERSION.SDK_INT<33) Toast.makeText(this@CouiActivity,"Для Liquid Glass требуется Android 13",Toast.LENGTH_SHORT).show()
       else { glass=it;appearance.edit { putBoolean("glass",it) } }
      }
      Line() }
      ToggleRow("Скрыть подписи","Оставить на панели только значки",hideLabels,iosStyle) { hideLabels=it;appearance.edit { putBoolean("hide_labels",it) } }
     }
     Text("В обычном режиме используется панель ColorOS. Доступность размытия зависит от устройства.",color=c.muted,fontSize=13.sp,lineHeight=19.sp,modifier=Modifier.padding(start=14.dp,end=14.dp,top=10.dp))
     Section("ДОСТУПНЫЙ СЛОВАРЬ")
     SurfaceCard { FeatureRow(R.drawable.ic_lucide_book_open,"Словарь Theme Store","Переводы и резервная копия") { openEditor() } }
    } else if(route==1) {
     ProfileHeader(ThemeStoreProfile, centered=profileTitleAlignment=="center", showIcon=showProfileIcon) { detail=false }
     Section("ШАБЛОН ПЕРЕВОДА")
     SurfaceCard {
      ChoiceRow("Язык перевода", listOf("Не выбран", "Русский"), listOf("none", "ru"), translationTemplate) {
       translationTemplate=it
       local.edit { putString("theme_store_template",it) }
       Toast.makeText(this@CouiActivity,"Чтобы применить шаблон, остановите Theme Store и откройте его снова",Toast.LENGTH_LONG).show()
      }
     }
     Text("Пока шаблон не выбран, Cobalt не переводит Theme Store. Русский шаблон хранится в приложении; пользовательские переводы имеют приоритет.",color=c.muted,fontSize=13.sp,lineHeight=19.sp,modifier=Modifier.padding(horizontal=14.dp,vertical=10.dp))
     Section("ПЕРЕВОД И ПОДКЛЮЧЕНИЕ")
     SurfaceCard {
      PlainRow("Словарь","Переводы, импорт и экспорт") { openEditor() }
      Line()
      Column(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=14.dp)) { Text("LSPosed",color=c.text,fontSize=16.sp,fontWeight=FontWeight.Medium);Spacer(Modifier.height(4.dp));Text("Включите модуль Cobalt в LSPosed и выберите Theme Store в области действия. Для применения настроек LSPosed может потребоваться остановить Theme Store и открыть его снова.",color=c.muted,fontSize=13.sp,lineHeight=18.sp) }
     }
    } else {
     Text(listOf("Главная","Приложения","О приложении")[page],color=c.text,fontSize=32.sp,fontWeight=FontWeight.SemiBold,modifier=Modifier.padding(top=18.dp,bottom=25.dp))
     when(page) {
      0 -> {
       Hero(onClick={ scope.launch { pager.animateScrollToPage(1) } })
       Section("БЫСТРЫЙ ДОСТУП")
       SurfaceCard { CobaltPreferenceRow("Приложения", "Доступные профили перевода") { scope.launch { pager.animateScrollToPage(1) } };Line();CobaltPreferenceRow("Настройки") { settings=true } }
       Section("СОСТОЯНИЕ СИСТЕМЫ")
       RootStatusCard()
       Spacer(Modifier.height(10.dp))
       PlatformStatusCard { startActivity(Intent(this@CouiActivity, LogsActivity::class.java)); overridePendingTransition(R.anim.cobalt_enter_right, R.anim.cobalt_exit_left) }
      }
      1 -> {
       if (!detail && !settings) FeatureSearchBar(searchState)
       Spacer(Modifier.height(18.dp))
       val apps = listOf(
        Triple("Theme Store", "com.heytap.themestore", R.drawable.ic_lucide_languages),
        Triple("Theme Store для OnePlus", "com.oplus.themestore", R.drawable.ic_lucide_languages),
       ).filter { query.isBlank() || it.first.contains(query, true) || it.second.contains(query, true) }
       if(apps.isEmpty()) Text("Приложения не найдены",color=c.muted,fontSize=14.sp,modifier=Modifier.padding(12.dp))
       else apps.forEach { (name, packageName, icon) ->
        SurfaceCard { FeatureRow(icon,name,packageName,true) { focusManager.clearFocus(force=true); detail=true } }
        Spacer(Modifier.height(10.dp))
       }
       Text("Профиль и словарь выбираются отдельно для каждого имени пакета и версии приложения.",fontSize=13.sp,lineHeight=19.sp,color=c.muted,modifier=Modifier.padding(14.dp))
      }
      else -> {
       SurfaceCard { Column(Modifier.fillMaxWidth().padding(22.dp),horizontalAlignment=Alignment.CenterHorizontally) { Image(painterResource(R.drawable.cobalt_logo),null,Modifier.size(72.dp).clip(RoundedCornerShape(20.dp)));Spacer(Modifier.height(12.dp));Text("Cobalt",fontSize=25.sp,fontWeight=FontWeight.SemiBold,color=c.text);Text("Перевод и адаптация ColorOS для китайского рынка",fontSize=13.sp,color=c.muted,textAlign=TextAlign.Center,modifier=Modifier.padding(top=5.dp));Text("Тестовая версия · 0.49",fontSize=12.sp,color=c.muted,modifier=Modifier.padding(top=8.dp)) } }
       Section("ПРИЛОЖЕНИЕ")
       SurfaceCard { CobaltPreferenceRow("Настройки") { settings=true };Line();CobaltPreferenceRow("Telegram · @elqaren","Связь с автором") { startActivity(Intent(Intent.ACTION_VIEW,"https://t.me/elqaren".toUri())) } }
       Section("О ПРОЕКТЕ")
       SurfaceCard {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
         Text("Перевод и адаптация ColorOS",fontSize=16.sp,lineHeight=21.sp,fontWeight=FontWeight.SemiBold,color=c.text)
         Spacer(Modifier.height(8.dp))
         Text("Cobalt объединяет инструменты для перевода и настройки поддерживаемых приложений ColorOS. Для каждого приложения доступны отдельные параметры и словарь, который можно дополнять собственными переводами.",fontSize=14.sp,lineHeight=21.sp,color=c.text)
         Spacer(Modifier.height(10.dp))
         Text("Проект использует LSPosed и, при необходимости, Cobalt Platform для изменений, которые невозможно внести с помощью перевода интерфейса.",fontSize=14.sp,lineHeight=21.sp,color=c.muted)
        }
        Line()
        CobaltPreferenceRow("Phontex", "Разработчик · GitHub") { startActivity(Intent(Intent.ACTION_VIEW,"https://github.com/phontex".toUri())) }
       }
       Section("СОВМЕСТИМОСТЬ")
       SurfaceCard { Column(Modifier.fillMaxWidth().padding(18.dp)) {
        Text("Поддерживаемые приложения",fontSize=16.sp,lineHeight=21.sp,fontWeight=FontWeight.SemiBold,color=c.text)
        Spacer(Modifier.height(6.dp))
        Text("Доступные функции зависят от приложения и версии прошивки.",fontSize=14.sp,lineHeight=20.sp,color=c.muted)
        Spacer(Modifier.height(10.dp))
        Text("Cobalt не является официальным продуктом OPPO.",fontSize=13.sp,lineHeight=18.sp,color=c.muted)
       } }
      }
     }
    }
   }
   }
   }
   if (!detail && !settings) {
   Box(Modifier.align(Alignment.BottomCenter)) {
   Box {
    val icons=listOf(R.drawable.ic_phosphor_homefill,R.drawable.ic_phosphor_appsfill,R.drawable.ic_phosphor_infofill).map { ImageVector.vectorResource(it) }
    val outlines=listOf(R.drawable.ic_phosphor_home,R.drawable.ic_phosphor_apps,R.drawable.ic_phosphor_info).map { ImageVector.vectorResource(it) }
    if(iosStyle) { IosBar(tab=tab,icons=outlines,hideLabels=hideLabels,onSelect={scope.launch { pager.animateScrollToPage(it) }}) } else if(glass && Build.VERSION.SDK_INT>=33) {
     val tabs=listOf(BottomTab(R.string.nav_home,icons[0],outlines[0],0),BottomTab(R.string.nav_apps,icons[1],outlines[1],1),BottomTab(R.string.nav_about,icons[2],outlines[2],2))
     LiquidGlassBottomNavigationBar(tabs=tabs,selectedIndex=tab,pagerState=pager,onTabSelected={scope.launch { pager.animateScrollToPage(it) }},backdrop=backdrop,hideLabels=hideLabels)
    } else ColorOs17BottomNavigationBar(items=listOf(NavigationItem("Главная",icons[0]),NavigationItem("Приложения",icons[1]),NavigationItem("О проекте",icons[2])),selectedIndex=tab,onItemSelected={scope.launch { pager.animateScrollToPage(it) }},selectedIcons=icons,unselectedIcons=outlines,hideLabels=hideLabels)
   }
   }
   }
  }
 }
 private fun openEditor() { startActivity(Intent(this,MainActivity::class.java)); overridePendingTransition(R.anim.cobalt_enter_right,R.anim.cobalt_exit_left) }
 @Composable private fun PageHeader(title:String,back:()->Unit) { val c=LocalDesign.current;Row(Modifier.fillMaxWidth().padding(top=5.dp),verticalAlignment=Alignment.CenterVertically) { CobaltBackButton(back);Text(title,color=c.text,fontSize=18.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(start=10.dp)) } }
 @Composable private fun Section(title:String) { Text(title,color=LocalDesign.current.text.copy(alpha=.62f),fontSize=13.sp,fontWeight=FontWeight.SemiBold,letterSpacing=0.8.sp,modifier=Modifier.padding(start=12.dp,top=20.dp,bottom=8.dp)) }
 @Composable private fun SurfaceCard(content:@Composable ColumnScope.()->Unit) {
  val c=LocalDesign.current
  val radius=if(LocalPastel.current) 28.dp else 26.dp
  val shape=RoundedCornerShape(radius)
  // A single quiet surface: no contrasting outline and no dark halo around the card.
  Column(Modifier.fillMaxWidth()
   .padding(bottom=2.dp)
   .shadow(if(c.dark) 1.dp else 2.dp, shape, ambientColor=Color.Black.copy(alpha=if(c.dark) .18f else .055f), spotColor=Color.Black.copy(alpha=if(c.dark) .22f else .08f))
   .clip(shape).background(c.surface),
   content=content)

 }
 @Composable private fun RootStatusCard() {
  val c=LocalDesign.current
  val status = rememberStatus()
  SurfaceCard { Column(Modifier.fillMaxWidth().padding(horizontal=18.dp, vertical=15.dp)) {
   Text("Root-доступ", color=c.text, fontSize=18.sp, fontWeight=FontWeight.SemiBold)
   Spacer(Modifier.height(4.dp))
   StatusRow("Состояние", status.root)
  } }
 }
 @Composable private fun PlatformStatusCard(onOpenLogs:()->Unit) {
  val c=LocalDesign.current
  val status=rememberStatus()
  SurfaceCard { Column(Modifier.fillMaxWidth().clickable { onOpenLogs() }.padding(horizontal=18.dp, vertical=15.dp)) {
   Row(verticalAlignment=Alignment.CenterVertically) {
    Column(Modifier.weight(1f)) {
     Text("Cobalt Platform", color=c.text, fontSize=18.sp, fontWeight=FontWeight.SemiBold)
     Spacer(Modifier.height(4.dp))
     Text("Системный компонент для применения профилей и диагностики", color=c.muted, fontSize=13.sp, lineHeight=18.sp)
    }
    Text("›", color=c.muted, fontSize=28.sp)
   }
   Spacer(Modifier.height(8.dp))
   StatusRow("Состояние", status.platform)
   Text("Нажмите, чтобы посмотреть логи", color=c.accent, fontSize=13.sp, modifier=Modifier.padding(top=2.dp))
  } }
 }
 @Composable private fun rememberStatus(): CobaltRuntimeStatus {
  val context=LocalContext.current
  val owner=LocalLifecycleOwner.current
  var refresh by remember { mutableIntStateOf(0) }
  var status by remember { mutableStateOf(CobaltRuntimeStatus.loading()) }
  DisposableEffect(owner) { val observer=LifecycleEventObserver { _, event -> if(event==Lifecycle.Event.ON_RESUME) refresh++ }; owner.lifecycle.addObserver(observer); onDispose { owner.lifecycle.removeObserver(observer) } }
  LaunchedEffect(refresh) { status=CobaltRuntimeChecker.check(context) }
  return status
 }
 @Composable private fun StatusRow(title:String,state:CobaltCheckState) {
  val c=LocalDesign.current
  val (label,color)=when(state) {
   CobaltCheckState.ACTIVE -> "Подтверждено" to Color(0xff2e9b62)
   CobaltCheckState.INACTIVE -> "Не обнаружено" to c.muted
   CobaltCheckState.UNKNOWN -> "Не удалось подтвердить" to Color(0xffc58b2a)
   CobaltCheckState.CHECKING -> "Проверка…" to c.muted
  }
  Row(Modifier.fillMaxWidth().padding(top=7.dp),verticalAlignment=Alignment.CenterVertically) {
   Box(Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(color)); Spacer(Modifier.width(9.dp)); Text(title,color=c.muted,fontSize=13.sp,modifier=Modifier.weight(1f)); Text(label,color=color,fontSize=13.sp,fontWeight=FontWeight.Medium)
  }
 }
 @Composable private fun IconBadge(res:Int,size:Int=40) {
  val tint=if(LocalPastel.current) when(res) { R.drawable.ic_lucide_book_open -> Color(0xff9872dc); R.drawable.ic_lucide_arrow_up_right -> Color(0xff6bb8dc); R.drawable.ic_lucide_shield_check -> Color(0xffbf8eae); else -> Color(0xffad83e3) } else when(res) { R.drawable.ic_lucide_book_open -> Color(0xff388df5); R.drawable.ic_lucide_settings_2, R.drawable.ic_lucide_sliders_horizontal -> Color(0xff9199a4); R.drawable.ic_lucide_arrow_up_right -> Color(0xff29a7e7); R.drawable.ic_lucide_shield_check -> Color(0xff9199a4); else -> Color(0xff3987f5) }
  val icon=when(res) { R.drawable.ic_lucide_book_open -> R.drawable.ic_phosphor_book; R.drawable.ic_lucide_settings_2, R.drawable.ic_lucide_sliders_horizontal -> R.drawable.ic_phosphor_settings; R.drawable.ic_lucide_arrow_up_right -> R.drawable.ic_phosphor_telegram; R.drawable.ic_lucide_shield_check -> R.drawable.ic_phosphor_shield; else -> R.drawable.ic_phosphor_translate }
  Box(Modifier.size(size.dp).then(if(LocalPastel.current) Modifier.clip(RoundedCornerShape(50)).background(tint.copy(alpha=if(LocalDesign.current.dark) .22f else .14f)) else Modifier),contentAlignment=Alignment.Center) { Icon(ImageVector.vectorResource(icon),null,tint=tint,modifier=Modifier.size((size*(if(LocalPastel.current) .62f else .68f)).dp)) }
 }
 @Composable private fun Line() { if (!hideDividers) Box(Modifier.padding(start=16.dp,end=16.dp).fillMaxWidth().height(0.33.dp).background(LocalDesign.current.line)) }
 @Composable private fun FeatureRow(icon:Int,title:String,subtitle:String,appIcon:Boolean=false,click:()->Unit) {
  val bitmap=if(appIcon) remember(title,subtitle) { runCatching { packageManager.getApplicationIcon(subtitle).toBitmap(96,96).asImageBitmap() }.getOrNull() } else null
  val leading: (@Composable () -> Unit)? = if (bitmap!=null) ({ Image(bitmap,null,Modifier.size(32.dp)) }) else null
  CobaltPreferenceRow(title,subtitle,leading,click)
 }
 @Composable private fun PlainRow(title:String,summary:String,click:()->Unit) { CobaltPreferenceRow(title,summary,onClick=click) }
 @Composable private fun Hero(onClick:()->Unit) {
  val dark=LocalDesign.current.dark
  val heroInteraction=remember { MutableInteractionSource() }
  val heroPressed by heroInteraction.collectIsPressedAsState()
  val heroScaleX by androidx.compose.animation.core.animateFloatAsState(if(heroPressed) .965f else 1f, androidx.compose.animation.core.spring(dampingRatio=.62f,stiffness=260f),label="heroWidth")
  val heroScaleY by androidx.compose.animation.core.animateFloatAsState(if(heroPressed) .984f else 1f, androidx.compose.animation.core.spring(dampingRatio=.58f,stiffness=210f),label="heroHeight")
  Column(Modifier.fillMaxWidth().graphicsLayer { scaleX=heroScaleX;scaleY=heroScaleY }.shadow(8.dp, RoundedCornerShape(26.dp), ambientColor=Color(0xff174888).copy(alpha=.20f), spotColor=Color(0xff174888).copy(alpha=.28f)).cobaltHeroBackground(dark,LocalPastel.current,if(LocalWallpaper.current) LocalDesign.current.accent else null).clip(RoundedCornerShape(26.dp)).clickable(interactionSource=heroInteraction,indication=null,role=Role.Button,onClick=onClick).padding(horizontal=20.dp,vertical=16.dp)) {
   Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
    Image(painterResource(R.drawable.cobalt_logo),null,Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)))
    Spacer(Modifier.width(9.dp))
    Text("COBALT",fontSize=12.sp,letterSpacing=2.sp,fontWeight=FontWeight.SemiBold,color=Color.White.copy(alpha=.9f),modifier=Modifier.weight(1f))
    Icon(ImageVector.vectorResource(R.drawable.ic_phosphor_chevron),null,tint=Color.White.copy(alpha=.75f),modifier=Modifier.size(18.dp))
   }
   Spacer(Modifier.height(14.dp))
   Text("ColorOS на вашем языке",fontSize=22.sp,lineHeight=27.sp,fontWeight=FontWeight.Bold,color=Color.White)
   Spacer(Modifier.height(10.dp))
   Text("Настройка переводов и собственный словарь для поддерживаемых приложений.",fontSize=13.sp,lineHeight=18.sp,color=Color.White.copy(alpha=.86f))
   Spacer(Modifier.height(20.dp))
   Text("ТЕСТОВАЯ ВЕРСИЯ",fontSize=10.sp,letterSpacing=1.sp,fontWeight=FontWeight.Medium,color=Color.White.copy(alpha=.9f),modifier=Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha=.13f)).padding(horizontal=10.dp,vertical=6.dp))
  }
 }
 @Composable private fun ToggleRow(title:String,description:String,checked:Boolean,iosStyle:Boolean,onChange:(Boolean)->Unit) {
  var switchPressed by remember { mutableStateOf(false) }
  BasicComponent(title=title,summary=description,insideMargin=PaddingValues(horizontal=16.dp,vertical=14.dp),onClick={onChange(!checked)},role=Role.Switch,holdDownState=switchPressed,endActions={ if(iosStyle) IosSwitch(checked,onChange) else ColorOsSwitch(checked=checked,onCheckedChange=onChange,onPressStateChange={switchPressed=it}) })
 }

 @Composable private fun ChoiceRow(title:String,labels:List<String>,values:List<String>,selected:String,onChange:(String)->Unit) {
  ColorOsWindowDropdownPreference(items=labels,selectedIndex=values.indexOf(selected).coerceAtLeast(0),title=title,dropdownColors=DropdownDefaults.dropdownColors(),onSelectedIndexChange={onChange(values[it])})
 }
}
