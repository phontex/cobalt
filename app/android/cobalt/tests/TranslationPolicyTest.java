import com.cobalt.app.TranslationPolicy;
import java.util.HashMap;
import java.util.Map;
public class TranslationPolicyTest {
 public static void main(String[] args) {
  Map<String,String> custom=new HashMap<>(); custom.put("功能设置","Своя запись"); custom.put("自定义文本","Пользовательский текст"); custom.put("限时特惠","Выгодное предложение"); custom.put("%1$s 已完成","%1$s готово");
  check(TranslationPolicy.disabled().translate("功能设置"),null);
  check(new TranslationPolicy("none",custom).translate("功能设置"),null);
  check(new TranslationPolicy("none",custom).translate("自定义文本"),null);
  check(new TranslationPolicy("other",custom).translate("功能设置"),null);
  check(new TranslationPolicy(null,custom).translate("功能设置"),null);
  TranslationPolicy russian=new TranslationPolicy("ru",custom);
  check(russian.translate("功能设置"),"Своя запись");
  check(russian.translate("手机精灵"),"Питомец");
  check(russian.translate("自定义文本"),"Пользовательский текст");
  check(russian.translate("  限时特惠  "),"Выгодное предложение");
  check(russian.translate("限时特惠 · 今日"),"Выгодное предложение · 今日");
  check(russian.translate("%d 已完成"),"%d готово");
  check(russian.translate("not known"),null);
  check(russian.translate(null),null);
  custom.put("功能设置","Изменено позднее"); check(russian.translate("功能设置"),"Своя запись");
  System.out.println("Translation policy tests passed (14 cases)");
 }
 static void check(String actual,String expected){if(!java.util.Objects.equals(actual,expected))throw new AssertionError("expected="+expected+", actual="+actual);}
}
