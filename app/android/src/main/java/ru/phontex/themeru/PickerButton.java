package ru.phontex.themeru;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.TextView;

/** In-window control; no overlay permission, screen capture or background service. */
@android.annotation.SuppressLint("ViewConstructor")
final class PickerButton extends TextView {
 private final ViewGroup root; private final boolean autoHide; private final Runnable action;
 private final float d; private final int slop; private float startX,startY,baseX,baseY;
 private boolean dragging,hidden,edgeVisible; private static float lastY=-1; private static boolean left=false;
 private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
 private final Path tip=new Path();
 private Shader circleShader;
 private final Runnable hideTask=()->hide();
 PickerButton(Context c,ViewGroup r,boolean h,Runnable click) {
  super(c);root=r;autoHide=h;action=click;d=c.getResources().getDisplayMetrics().density;slop=ViewConfiguration.get(c).getScaledTouchSlop();
  setBackgroundColor(Color.TRANSPARENT);setText("");setContentDescription("Выбрать надпись для перевода");setClickable(true);
 }
 void initialize(){post(()->{float maxX=Math.max(0,root.getWidth()-getWidth());setX(left?0:maxX);setY(Math.max(0,Math.min(root.getHeight()-getHeight(),lastY<0?(root.getHeight()-getHeight())*.5f:lastY)));schedule();});}
 private void schedule(){removeCallbacks(hideTask);if(autoHide && isAttachedToWindow())postDelayed(hideTask,6500);}
 private void hide(){
  if(!autoHide||!isAttachedToWindow())return;
  hidden=true;left=getX()+getWidth()/2f<root.getWidth()/2f;
  float target=left?-getWidth()+20*d:root.getWidth()-20*d;
  animate().cancel();animate().x(target).setDuration(380).setInterpolator(new DecelerateInterpolator(1.7f)).withEndAction(()->{if(hidden){edgeVisible=true;invalidate();}}).start();
 }
 private void reveal(){hidden=false;edgeVisible=false;invalidate();animate().cancel();animate().x(left?0:Math.max(0,root.getWidth()-getWidth())).setDuration(420).setInterpolator(new OvershootInterpolator(.8f)).start();schedule();}
 @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){
  super.onSizeChanged(w,h,oldw,oldh);float radius=20*d;float cx=w/2f,cy=h/2f;
  circleShader=new LinearGradient(cx-radius,cy-radius,cx+radius,cy+radius,0xC7777B80,0xD0393B40,Shader.TileMode.CLAMP);
 }
 @Override protected void onDraw(Canvas canvas){
  float cx=getWidth()/2f, cy=getHeight()/2f;
  paint.setShader(null);paint.setStyle(Paint.Style.FILL);
  if(edgeVisible){
   float x=left?getWidth()-6*d:2*d;
   paint.setColor(0x66000000);canvas.drawRoundRect(x-d,cy-13*d,x+5*d,cy+13*d,3*d,3*d,paint);
   paint.setColor(Color.WHITE);canvas.drawRoundRect(x,cy-12*d,x+4*d,cy+12*d,2*d,2*d,paint);return;
  }
  float radius=20*d;
  // Restrained translucent graphite, inspired by the control centre. It is not a backdrop blur.
  paint.setColor(0x26000000);canvas.drawCircle(cx,cy+1.5f*d,radius+1*d,paint);
  paint.setShader(circleShader);
  canvas.drawCircle(cx,cy,radius,paint);paint.setShader(null);
  paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(.8f*d);paint.setColor(0x70FFFFFF);canvas.drawCircle(cx,cy,radius-.5f*d,paint);
  paint.setStyle(Paint.Style.FILL);paint.setColor(0xF5FFFFFF);
  canvas.save();canvas.rotate(45,cx,cy);
  canvas.drawRoundRect(cx-2.4f*d,cy-10*d,cx+2.4f*d,cy+4.5f*d,1.5f*d,1.5f*d,paint);
  tip.reset();tip.moveTo(cx-2.4f*d,cy+5.1f*d);tip.lineTo(cx+2.4f*d,cy+5.1f*d);tip.lineTo(cx,cy+9.5f*d);tip.close();canvas.drawPath(tip,paint);
  canvas.restore();
 }
 @Override public CharSequence getAccessibilityClassName(){return android.widget.Button.class.getName();}
 @Override public boolean onTouchEvent(MotionEvent e){switch(e.getActionMasked()){
  case MotionEvent.ACTION_DOWN:removeCallbacks(hideTask);animate().cancel();startX=e.getRawX();startY=e.getRawY();baseX=getX();baseY=getY();dragging=false;return true;
  case MotionEvent.ACTION_MOVE:
   float dx=e.getRawX()-startX,dy=e.getRawY()-startY;
   if(!dragging && Math.hypot(dx,dy)>slop){dragging=true;if(hidden){hidden=false;edgeVisible=false;invalidate();baseX=left?0:root.getWidth()-getWidth();}}
   if(dragging){setX(Math.max(0,Math.min(root.getWidth()-getWidth(),baseX+dx)));setY(Math.max(0,Math.min(root.getHeight()-getHeight(),baseY+dy)));}return true;
  case MotionEvent.ACTION_UP:
   if(dragging){lastY=getY();left=getX()+getWidth()/2f<root.getWidth()/2f;animate().x(left?0:Math.max(0,root.getWidth()-getWidth())).setDuration(350).setInterpolator(new OvershootInterpolator(.7f)).start();schedule();}
   else if(hidden){reveal();}else{performClick();schedule();}return true;
  case MotionEvent.ACTION_CANCEL:schedule();return true;
 }return true;}
 @Override public boolean performClick(){super.performClick();action.run();return true;}
 @Override protected void onDetachedFromWindow(){removeCallbacks(hideTask);animate().cancel();super.onDetachedFromWindow();}
}
