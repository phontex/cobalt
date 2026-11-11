package app.cobalt.translate
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cobalt.translate.R

@Composable fun CobaltSearch(state: TextFieldState) {
 val c=LocalDesign.current
 Row(Modifier.fillMaxWidth().padding(vertical=8.dp).height(42.dp).clip(RoundedCornerShape(22.dp)).background(if(c.dark) Color(0xff242424) else Color(0xffe5e8ed)).padding(horizontal=14.dp),verticalAlignment=Alignment.CenterVertically) {
  Icon(ImageVector.vectorResource(R.drawable.ic_phosphor_search),null,tint=c.muted,modifier=Modifier.size(20.dp))
  Spacer(Modifier.width(8.dp))
  Box(Modifier.weight(1f),contentAlignment=Alignment.CenterStart) {
   if(state.text.isEmpty()) Text("Поиск приложений",color=c.muted,fontSize=14.sp)
   BasicTextField(state=state,modifier=Modifier.fillMaxWidth().semantics { contentDescription="Поиск приложений" },textStyle=TextStyle(color=c.text,fontSize=14.sp),cursorBrush=SolidColor(c.accent))
  }
 }
}
@Composable fun IosSwitch(checked:Boolean,onChange:(Boolean)->Unit) {
 val bg by animateColorAsState(if(checked) Color(0xff34c759) else Color(0xff767680),label="iosTrack")
 val x by animateDpAsState(if(checked) 22.dp else 2.dp,spring(dampingRatio=.8f,stiffness=500f),label="iosThumb")
 Box(Modifier.size(width=50.dp,height=30.dp).clip(CircleShape).background(bg).clickable(role=Role.Switch) { onChange(!checked) },contentAlignment=Alignment.CenterStart) {
  Box(Modifier.offset { IntOffset(x.roundToPx(), 0) }.size(26.dp).clip(CircleShape).background(Color.White))
 }
}
@Composable fun IosBar(tab:Int,icons:List<ImageVector>,hideLabels:Boolean,onSelect:(Int)->Unit) {
 val c=LocalDesign.current
 Row(Modifier.padding(start=42.dp,end=42.dp,bottom=16.dp).height(if(hideLabels) 54.dp else 62.dp).fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(if(c.dark) Color(0xff303036) else Color(0xfff7f8fa)),verticalAlignment=Alignment.CenterVertically) {
  listOf("Главная","Приложения","О проекте").forEachIndexed { i,title ->
   val tint by animateColorAsState(if(i==tab) Color(0xff007aff) else c.muted,label="iosTab")
   Column(Modifier.weight(1f).fillMaxHeight().clickable(role=Role.Tab){ onSelect(i) }.semantics { contentDescription=title },horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
    Icon(icons[i],null,tint=tint,modifier=Modifier.size(23.dp))
    if(!hideLabels) Text(title,color=tint,fontSize=10.sp,maxLines=1)
   }
  }
 }
}
