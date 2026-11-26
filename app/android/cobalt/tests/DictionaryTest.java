import com.cobalt.app.Dictionary;
public class DictionaryTest {
 public static void main(String[] args) {
  check("功能设置", "Настройки функций"); check("手机精灵", "Питомец");
  check("每日补给", "Бонус"); check("月卡", "VIP");
  check("饥饿：85%", "Голод: 85%"); check("心情: 100%", "Настр.: 100%"); check("健康: 100%", "Здор.: 100%");
  check("挂件开关", "Виджет");
  if (Dictionary.translate("неизвестно") != null) throw new AssertionError("unexpected replacement");
  System.out.println("Dictionary tests passed");
 }
 static void check(String original, String expected) { String v=Dictionary.translate(original); if (!expected.equals(v)) throw new AssertionError(original+" => "+v); }
}
