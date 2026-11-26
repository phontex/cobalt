import com.cobalt.app.RestartCommands;
public class RestartCommandsTest {
 public static void main(String[] args) {
  if (!"am force-stop com.heytap.themestore".equals(RestartCommands.forceStop("com.heytap.themestore"))) throw new AssertionError("Theme Store command");
  for (String bad : new String[]{"com.android.systemui", "com.example.other", "com.example.cobalt", "com.cobalt.app", "com.heytap.themestore;reboot", "", "com.heytap.themestore extra"}) {
   try { RestartCommands.forceStop(bad); throw new AssertionError("Allowed: " + bad); } catch (IllegalArgumentException expected) { }
  }
  try { RestartCommands.forceStop(null); throw new AssertionError("Allowed null"); } catch (IllegalArgumentException expected) { }
  System.out.println("Restart command tests passed (1 allowed, 8 rejected)");
 }
}
