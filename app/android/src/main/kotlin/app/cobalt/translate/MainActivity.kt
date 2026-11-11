package app.cobalt.translate

import androidx.core.content.edit

import android.app.Activity
import android.annotation.SuppressLint
import io.github.suqi8.coui.kmp.basic.TextField as CouiTextField
import io.github.suqi8.coui.kmp.basic.TextFieldMode
import app.cobalt.ui.ui.common.CouiConfirmDialog
import app.cobalt.ui.ui.common.ColorOs17Button
import app.cobalt.ui.ui.common.AppIcons
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.*
import io.github.suqi8.coui.kmp.basic.BasicComponent
import androidx.compose.ui.semantics.Role
import app.cobalt.ui.ui.common.ColorOsSwitch
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.theme.darkColorScheme
import io.github.suqi8.coui.kmp.basic.Card
import io.github.suqi8.coui.kmp.basic.CardDefaults
import org.json.JSONObject
import app.cobalt.translate.R

class MainActivity : ComponentActivity() {
 private val prefs by lazy { getSharedPreferences("local",0) }
 private fun data(): JSONObject = try { JSONObject(prefs.getString("dictionary","{}") ?: "{}") } catch(e:Exception) { JSONObject() }
 @SuppressLint("UseKtx")
 private fun save(data: JSONObject): Boolean = data.length() <= 5000 && data.toString().toByteArray(Charsets.UTF_8).size <= 1048576 && prefs.edit().putString("dictionary",data.toString()).commit()
 private fun message(t:String) = Toast.makeText(this,t,Toast.LENGTH_LONG).show()
 private var stopBusy by mutableStateOf(false)
 private var refresh by mutableIntStateOf(0)
 private var pendingImport by mutableStateOf<JSONObject?>(null)
 private var pendingImportCount by mutableIntStateOf(0)
 private var selectionMode by mutableStateOf(false)
 private var initialOriginal by mutableStateOf("")
 private var initialTranslation by mutableStateOf("")
 override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); enableEdgeToEdge()
  val original=intent?.getStringExtra("original")
  if(original!=null) {selectionMode=true;initialOriginal=original;initialTranslation=data().optString(original,intent?.getStringExtra("translated") ?: "")}
  setContent { val appearance=remember { getSharedPreferences("appearance",0) }; DesignTheme(this,appearance.getString("theme_mode","system") ?: "system",appearance.getString("ui_size","normal") ?: "normal") { Editor() } }
 }
 @Composable private fun Editor() {
  var original by rememberSaveable { mutableStateOf(initialOriginal) };var translation by rememberSaveable { mutableStateOf(initialTranslation) }
  var picker by remember { mutableStateOf(prefs.getBoolean("picker",false)) };var autoHide by remember { mutableStateOf(prefs.getBoolean("picker_hide",false)) };var showList by remember { mutableStateOf(false) };var selected by remember { mutableStateOf<String?>(null) }
  val count=remember(refresh) {data().length()}
  Column(Modifier.fillMaxSize().background(LocalDesign.current.background).safeDrawingPadding().verticalScroll(rememberScrollState()).padding(start=18.dp,end=18.dp,top=4.dp,bottom=24.dp)) {
   val appearance=remember { getSharedPreferences("appearance",0) }
   ProfileHeader(ThemeStoreProfile, centered=appearance.getString("profile_title_alignment","left")=="center", showIcon=appearance.getBoolean("profile_show_icon",true), title=if(selectionMode) "Перевод надписи" else "Словарь", showAction=false) { finish(); overridePendingTransition(R.anim.cobalt_enter_left,R.anim.cobalt_exit_right) }
   Spacer(Modifier.height(8.dp))
   if (!selectionMode) EditorSection("ВЫБОР НАДПИСЕЙ")
   if (!selectionMode) Panel {
    EditorToggle("Выбор надписей","Показывать кнопку выбора в Theme Store",picker) { picker=it;prefs.edit { putBoolean("picker",it) };message("Вернитесь в Theme Store, чтобы применить изменение") }
    if(picker) {
     DividerLine()
     EditorToggle("Скрывать кнопку у края","Сворачивать кнопку в полоску после бездействия",autoHide) { autoHide=it;prefs.edit { putBoolean("picker_hide",it) };message("Вернитесь в Theme Store, чтобы применить изменение") }
    }
   }
   if(!selectionMode && picker) {
    val seen=prefs.getLong("picker_last_seen",0L)
    Text(if(seen>0L) "Последнее добавление кнопки ${prefs.getString("picker_runtime_version","")}: ${java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT,java.text.DateFormat.SHORT).format(java.util.Date(seen))}. Экран: ${prefs.getString("picker_last_activity", "неизвестно")}." else "Кнопка ещё не была обнаружена в Theme Store. Проверьте настройки модуля в LSPosed.",color=LocalDesign.current.muted,fontSize=12.sp,lineHeight=18.sp,modifier=Modifier.padding(horizontal=12.dp,vertical=8.dp))
   }
   EditorSection(if(selectionMode) "ВЫБРАННАЯ НАДПИСЬ" else "НОВЫЙ ПЕРЕВОД")
   Panel { Column(Modifier.padding(16.dp)) {
    CouiTextField(value=original,onValueChange={original=it},modifier=Modifier.fillMaxWidth(),label="Оригинал",backgroundMode=TextFieldMode.Rectangle,minLines=2)
    Spacer(Modifier.height(12.dp));CouiTextField(value=translation,onValueChange={translation=it},modifier=Modifier.fillMaxWidth(),label="Перевод",backgroundMode=TextFieldMode.Rectangle,minLines=2)
    Spacer(Modifier.height(18.dp));ColorOs17Button(text=if(stopBusy) "Остановка Theme Store…" else "Сохранить перевод",primary=true,onClick={ if(stopBusy) return@ColorOs17Button; if(original.isBlank() || translation.isBlank() || original.length>4096 || translation.length>4096) message("Введите оригинал и перевод: не более 4096 символов в каждом поле") else try {val j=data();j.put(original,translation);if(save(j)){refresh++;if(!selectionMode){original="";translation=""};stopAfterSave()}else message("Не удалось сохранить: словарь слишком велик")}catch(e:Exception){message("Ошибка сохранения")} },modifier=Modifier.fillMaxWidth(),minimumHeight=48.dp)
   } }
   if(!selectionMode) Text("При сохранении Cobalt попытается остановить Theme Store через root. После остановки откройте Theme Store снова.",color=LocalDesign.current.muted,fontSize=13.sp,lineHeight=19.sp,modifier=Modifier.padding(horizontal=14.dp,vertical=10.dp))
   if(!selectionMode) {
   EditorSection("МОИ ПЕРЕВОДЫ")
   Panel { Link("Пользовательский словарь", "Записей: $count") {showList=true}; DividerLine();Link("Импорт JSON", "Совпадающие записи заменяются") { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).setType("application/json").addCategory(Intent.CATEGORY_OPENABLE),11) };DividerLine();Link("Экспорт JSON", "Сохранить резервную копию") {startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/json").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"cobalt-dictionary.json"),12)} }
   Text("Пользовательские переводы применяются при выборе русского шаблона и имеют приоритет над встроенными. Выбор надписей доступен не для всех элементов интерфейса.",color=LocalDesign.current.muted,fontSize=13.sp,modifier=Modifier.padding(horizontal=16.dp,vertical=18.dp));Spacer(Modifier.height(32.dp))
   } else Text("При сохранении Cobalt попытается остановить Theme Store через root. После остановки откройте Theme Store снова.",color=LocalDesign.current.muted,fontSize=13.sp,lineHeight=19.sp,modifier=Modifier.padding(horizontal=14.dp,vertical=14.dp))
  }
  pendingImport?.let { pending -> CouiConfirmDialog(show=true,title="Импорт словаря",summary="Записей: $pendingImportCount. Совпадающие записи будут заменены.",negativeText="Отмена",positiveText="Импортировать",onDismissRequest={pendingImport=null},onPositive={if(save(pending)){refresh++;message("Импортировано: $pendingImportCount")}else message("Не удалось сохранить словарь");pendingImport=null}) }
  if(showList){val j=remember(refresh){data()};val keys=remember(refresh){j.keys().asSequence().toList().sorted()}; CouiConfirmDialog(show=true,title="Мои переводы: ${keys.size}",summary=null,negativeText="",positiveText="Закрыть",onDismissRequest={showList=false},onPositive={showList=false},content={ if(keys.isEmpty()) Text("Словарь пуст",color=LocalDesign.current.muted) else LazyColumn(Modifier.heightIn(max=350.dp)) { items(keys, key={ it }) { key -> Column(Modifier.fillMaxWidth().clickable { selected=key;showList=false }.padding(vertical=10.dp)) { Text(key,color=LocalDesign.current.text);Text(j.optString(key),color=LocalDesign.current.muted,fontSize=13.sp) } } } }) }
  selected?.let { key -> CouiConfirmDialog(show=true,title=key,summary=data().optString(key),negativeText="Удалить",positiveText="Изменить",onDismissRequest={selected=null},onPositive={original=key;translation=data().optString(key);selected=null},onNegative={val j=data();j.remove(key);if(save(j)){refresh++;message("Удалено")}else message("Ошибка сохранения");selected=null}) }

 }
 private fun stopAfterSave() {
  stopBusy = true
  lifecycleScope.launch {
   val command = runCatching { RestartCommands.forceStop(ThemeStoreProfile.packageName) }.getOrNull()
   val error = if (command == null) "Остановка недоступна" else requestRoot(command)
   stopBusy = false
   if (error == null) message("Перевод сохранён. Команда остановки отправлена; откройте Theme Store заново.")
   else message("Перевод сохранён, но остановить Theme Store не удалось. $error Остановите приложение вручную и откройте его снова.")
   if (selectionMode) { finish(); overridePendingTransition(R.anim.cobalt_enter_left,R.anim.cobalt_exit_right) }
  }
 }
 @Composable private fun EditorSection(title:String) { Text(title,color=LocalDesign.current.muted,fontSize=12.sp,fontWeight=FontWeight.Bold,letterSpacing=.5.sp,modifier=Modifier.padding(start=12.dp,top=20.dp,bottom=8.dp)) }
 @Composable private fun Panel(content:@Composable ()->Unit){val c=LocalDesign.current;Card(modifier=Modifier.fillMaxWidth().shadow(2.dp,RoundedCornerShape(if(LocalPastel.current) 28.dp else 26.dp),ambientColor=Color.Black.copy(alpha=.07f),spotColor=Color.Black.copy(alpha=.10f)),cornerRadius=if(LocalPastel.current) 28.dp else 20.dp,insideMargin=PaddingValues(0.dp),colors=CardDefaults.defaultColors(color=c.surface,contentColor=c.text)){content()} }
 @Composable private fun Link(title:String,summary:String,onClick:()->Unit){CobaltPreferenceRow(title,summary,onClick=onClick)}
 @Composable private fun EditorToggle(title:String,summary:String,checked:Boolean,onChange:(Boolean)->Unit) {
  val ios=getSharedPreferences("appearance",0).getString("ui_style","oppo")=="ios"
  BasicComponent(title=title,summary=summary,insideMargin=PaddingValues(horizontal=16.dp,vertical=10.dp),onClick={onChange(!checked)},role=Role.Switch,endActions={if(ios) IosSwitch(checked,onChange) else ColorOsSwitch(checked=checked,onCheckedChange=onChange)})
 }
 @Composable private fun DividerLine(){if (!getSharedPreferences("appearance",0).getBoolean("hide_dividers",false)) Box(Modifier.padding(horizontal=18.dp).fillMaxWidth().height(0.33.dp).background(LocalDesign.current.line))}
 @Deprecated("Legacy result API") override fun onActivityResult(requestCode:Int,resultCode:Int,result:Intent?){super.onActivityResult(requestCode,resultCode,result);val uri=result?.data ?: return;if(resultCode!=Activity.RESULT_OK)return
  try { if(requestCode==12){val j=JSONObject().put("format",1).put("translations",data());contentResolver.openOutputStream(uri,"wt")!!.use{it.write(j.toString(2).toByteArray(Charsets.UTF_8))};message("Словарь экспортирован")}
   else if(requestCode==11){val bytes=contentResolver.openInputStream(uri)!!.use{input-> val output=java.io.ByteArrayOutputStream();val b=ByteArray(4096);while(true){val n=input.read(b);if(n<0)break;output.write(b,0,n);if(output.size()>1048576)throw Exception("Файл больше 1 МБ")};output.toByteArray()};val root=JSONObject(String(bytes,Charsets.UTF_8));if(root.optInt("format")!=1)throw Exception("Неизвестный формат");val entries=root.getJSONObject("translations");val merged=data();val it=entries.keys();var count=0;while(it.hasNext()){val key=it.next();val value=entries.get(key);if(value !is String||key.isBlank()||value.isBlank()||key.length>4096||value.length>4096)throw Exception("Некорректная запись");merged.put(key,value);count++};pendingImportCount=count;pendingImport=merged }
  }catch(e:Exception){message("Ошибка файла: ${e.message}")}
 }
}
