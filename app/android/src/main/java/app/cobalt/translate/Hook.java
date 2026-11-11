package app.cobalt.translate;
import android.app.Activity;import android.content.*;import android.database.Cursor;import android.graphics.Rect;import android.net.Uri;import android.view.*;import android.widget.*;import java.util.*;import de.robv.android.xposed.*;import de.robv.android.xposed.callbacks.XC_LoadPackage;
public final class Hook implements IXposedHookLoadPackage {
 static final String TAG="cobalt_picker_v1";static final Uri URI=Uri.parse("content://app.cobalt.translate.dictionary/entries");static volatile TranslationPolicy translations=TranslationPolicy.disabled();static volatile boolean loaded=false,picker=false,autoHide=false;static final Map<TextView,String> originals=Collections.synchronizedMap(new WeakHashMap<TextView,String>());static final Map<Activity,Boolean> choosing=Collections.synchronizedMap(new WeakHashMap<Activity,Boolean>());
 static void load(Context context) {
  try (Cursor cursor = context.getContentResolver().query(URI, null, null, null, null)) {
   if (cursor == null) throw new IllegalStateException("Dictionary provider returned no data");
   Map<String, String> custom = new HashMap<>();
   String template = "none";
   boolean pickerEnabled = false, hide = false;
   while (cursor.moveToNext()) {
    String key = cursor.getString(0), value = cursor.getString(1);
    if (key == null) pickerEnabled = "1".equals(value);
    else if ("\u0001cobalt_picker_hide".equals(key)) hide = "1".equals(value);
    else if ("\u0001cobalt_template".equals(key)) template = value;
    else custom.put(key, value);
   }
   translations = new TranslationPolicy(template, custom);
   picker = pickerEnabled;
   autoHide = hide;
  } catch (Throwable e) {
   // Fail closed if settings cannot be read.
   translations = TranslationPolicy.disabled();
   picker = false;
   autoHide = false;
   XposedBridge.log("Cobalt: cannot load settings: " + e);
  } finally { loaded = true; }
 }
 public void handleLoadPackage(XC_LoadPackage.LoadPackageParam pkg){if(!"com.heytap.themestore".equals(pkg.packageName) && !"com.oplus.themestore".equals(pkg.packageName))return;
 XC_MethodHook text=new XC_MethodHook(){protected void beforeHookedMethod(MethodHookParam p){try{if(!(p.thisObject instanceof TextView)||p.thisObject instanceof EditText)return;TextView view=(TextView)p.thisObject;if(!loaded)load(view.getContext());if(!(p.args[0] instanceof CharSequence)){originals.remove(view);return;}String input=p.args[0].toString();if(input.length()==0||input.length()>4096){originals.remove(view);return;}TranslationPolicy active=translations;String out=active.translate(input);if(picker && containsChinese(input))originals.put(view,input);else {String old=originals.get(view);String prior=old==null?null:active.translate(old);if(!picker || !input.equals(prior))originals.remove(view);}if(out!=null)p.args[0]=out;}catch(Throwable e){XposedBridge.log("Cobalt text: "+e);}}};
 XC_MethodHook hint=new XC_MethodHook(){protected void beforeHookedMethod(MethodHookParam p){try{if(!(p.thisObject instanceof TextView)||p.thisObject instanceof EditText||!(p.args[0] instanceof CharSequence))return;String input=p.args[0].toString();if(input.length()==0||input.length()>4096)return;if(!loaded)load(((TextView)p.thisObject).getContext());TranslationPolicy active=translations;String out=active.translate(input);if(out!=null)p.args[0]=out;}catch(Throwable e){XposedBridge.log("Cobalt hint: "+e);}}};
 try{XposedHelpers.findAndHookMethod(TextView.class,"setText",CharSequence.class,TextView.BufferType.class,text);}catch(Throwable e){XposedBridge.log("Cobalt: "+e);}
 try{XposedHelpers.findAndHookMethod(TextView.class,"setHint",CharSequence.class,hint);}catch(Throwable e){XposedBridge.log("Cobalt hint: "+e);}
 try{XposedHelpers.findAndHookMethod(Activity.class,"onResume",new XC_MethodHook(){protected void afterHookedMethod(MethodHookParam p){
  try{Activity a=(Activity)p.thisObject;load(a);ViewGroup root=(ViewGroup)a.getWindow().getDecorView();int generation=nextGeneration(a);
   // Base Activity.onResume can run before the target's override finishes configuring its window.
   // Install after the full lifecycle callback, then perform one bounded check for replacement.
   root.post(()->{if(isCurrent(a,generation))ensurePicker(a,root);});
   root.postDelayed(()->{if(isCurrent(a,generation))ensurePicker(a,root);},750);
  }catch(Throwable e){XposedBridge.log("Cobalt picker resume: "+e);}
 }});}catch(Throwable e){XposedBridge.log("Cobalt picker resume hook: "+e);}
 try{XposedHelpers.findAndHookMethod(Activity.class,"onPause",new XC_MethodHook(){protected void beforeHookedMethod(MethodHookParam p){Activity a=(Activity)p.thisObject;nextGeneration(a);choosing.remove(a);}});}catch(Throwable e){XposedBridge.log("Cobalt picker pause hook: "+e);}
 try{XposedHelpers.findAndHookMethod(Activity.class,"dispatchTouchEvent",MotionEvent.class,new XC_MethodHook(){protected void beforeHookedMethod(MethodHookParam p){try{Activity a=(Activity)p.thisObject;if(!Boolean.TRUE.equals(choosing.get(a)))return;MotionEvent ev=(MotionEvent)p.args[0];if(ev.getActionMasked()==MotionEvent.ACTION_CANCEL){choosing.remove(a);p.setResult(true);return;}if(ev.getActionMasked()==MotionEvent.ACTION_DOWN){p.setResult(true);return;}if(ev.getActionMasked()==MotionEvent.ACTION_UP){choosing.remove(a);TextView t=find(a.getWindow().getDecorView(),(int)ev.getRawX(),(int)ev.getRawY());if(t==null)Toast.makeText(a,"Текст не найден",Toast.LENGTH_SHORT).show();else{String orig=originals.get(t);if(orig==null && containsChinese(t.getText().toString()))orig=t.getText().toString();if(orig==null){Toast.makeText(a,"Исходный текст недоступен. Переоткройте экран.",Toast.LENGTH_LONG).show();}else{Intent i=new Intent();i.setClassName("app.cobalt.translate","app.cobalt.translate.MainActivity");i.putExtra("original",orig);i.putExtra("translated",t.getText().toString());a.startActivity(i);}}p.setResult(true);}else p.setResult(true);}catch(Throwable e){choosing.remove((Activity)p.thisObject);XposedBridge.log("Cobalt selection: "+e);}}});}catch(Throwable e){XposedBridge.log("Cobalt touch: "+e);}
 }
 static final Map<Activity,Integer> pickerGenerations=Collections.synchronizedMap(new WeakHashMap<Activity,Integer>());
 static int nextGeneration(Activity a){synchronized(pickerGenerations){int n=pickerGenerations.containsKey(a)?pickerGenerations.get(a)+1:1;pickerGenerations.put(a,n);return n;}}
 static boolean isCurrent(Activity a,int n){synchronized(pickerGenerations){return !a.isFinishing() && !a.isDestroyed() && Integer.valueOf(n).equals(pickerGenerations.get(a));}}
 static void ensurePicker(Activity a,ViewGroup root){try{
  View old=root.findViewWithTag(TAG);
  if(!picker){if(old!=null && old.getParent() instanceof ViewGroup)((ViewGroup)old.getParent()).removeView(old);return;}
  if(old instanceof PickerButton && old.getParent()==root)return;
  if(old!=null && old.getParent() instanceof ViewGroup)((ViewGroup)old.getParent()).removeView(old);
  PickerButton button=new PickerButton(a,root,autoHide,()->{choosing.put(a,true);Toast.makeText(a,"Коснитесь надписи для перевода",Toast.LENGTH_SHORT).show();});
  button.setTag(TAG);int size=(int)(52*a.getResources().getDisplayMetrics().density);
  root.addView(button,new FrameLayout.LayoutParams(size,size,Gravity.TOP|Gravity.START));button.initialize();
  try{android.os.Bundle report=new android.os.Bundle();report.putString("activity",a.getClass().getName());a.getContentResolver().call(URI,"picker_report_v44",null,report);}catch(Throwable e){XposedBridge.log("Cobalt picker report: "+e);}
 }catch(Throwable e){XposedBridge.log("Cobalt picker install: "+e);}}
 static boolean containsChinese(String s){for(int i=0;i<s.length();i++){char c=s.charAt(i);if(c>=0x3400&&c<=0x9fff)return true;}return false;}
 static TextView find(View v,int x,int y){if(v.getVisibility()!=View.VISIBLE||v.getAlpha()==0)return null;Rect r=new Rect();if(!v.getGlobalVisibleRect(r)||!r.contains(x,y))return null;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=g.getChildCount()-1;i>=0;i--){TextView found=find(g.getChildAt(i),x,y);if(found!=null)return found;}}if(v instanceof TextView && !(v instanceof EditText) && !TAG.equals(v.getTag()) && (originals.containsKey(v) || containsChinese(((TextView)v).getText().toString())))return (TextView)v;return null;}
}