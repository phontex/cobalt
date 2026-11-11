package app.cobalt.translate;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Process;
import org.json.JSONObject;
import java.util.Iterator;

/**
 * Read-only bridge from the Theme Store process to Cobalt's local preferences. The bridge must
 * be exported for LSPosed's target process, so every operation checks both the exact URI and the
 * Binder caller identity. No write operation is exposed.
 */
public final class DictionaryProvider extends ContentProvider {
 private static final String AUTHORITY = "app.cobalt.translate.dictionary";
 private static final String[] TARGET_PACKAGES = {"com.heytap.themestore", "com.oplus.themestore"};
 private static final String ENTRY_PATH = "/entries";
 private static final String PREFS = "local";

 @Override public boolean onCreate() { return getContext() != null; }

 private static boolean isTarget(String name) { for (String target : TARGET_PACKAGES) if (target.equals(name)) return true; return false; }

 private void check(Uri uri) {
  Context context = getContext();
  if (context == null || uri == null || !AUTHORITY.equals(uri.getAuthority())
      || !ENTRY_PATH.equals(uri.getPath()) || uri.getQuery() != null || uri.getFragment() != null) {
   throw new SecurityException("Invalid Cobalt provider URI");
  }
  final int uid = Binder.getCallingUid();
  if (uid == Process.myUid()) return;
  try {
   String caller = getCallingPackage();
   if (caller != null) {
    if (isTarget(caller)
        && context.getPackageManager().getApplicationInfo(caller, 0).uid == uid) return;
   } else {
    // Some framework-mediated calls do not expose a package attribution. In that case accept
    // the target only if Android reports it as belonging to the actual Binder UID.
    String[] packages = context.getPackageManager().getPackagesForUid(uid);
    if (packages != null) {
     for (String name : packages) {
      if (isTarget(name)
          && context.getPackageManager().getApplicationInfo(name, 0).uid == uid) return;
     }
    }
   }
  } catch (PackageManager.NameNotFoundException ignored) {
   // Deny callers whose package identity cannot be verified.
  }
  throw new SecurityException("Caller is not Theme Store");
 }

 @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) {
  check(uri);
  Context context = getContext();
  if (context == null) throw new IllegalStateException("Provider is not attached");
  MatrixCursor cursor = new MatrixCursor(new String[]{"original", "translated"});
  android.content.SharedPreferences pref = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
  cursor.addRow(new Object[]{null, pref.getBoolean("picker", false) ? "1" : "0"});
  cursor.addRow(new Object[]{"\u0001cobalt_picker_hide", pref.getBoolean("picker_hide", false) ? "1" : "0"});
  cursor.addRow(new Object[]{"\u0001cobalt_template", pref.getString("theme_store_template", "none")});
  try {
   JSONObject dictionary = new JSONObject(pref.getString("dictionary", "{}"));
   Iterator<String> keys = dictionary.keys();
   while (keys.hasNext()) {
    String key = keys.next();
    if (!key.startsWith("\u0001cobalt_")) cursor.addRow(new Object[]{key, dictionary.getString(key)});
   }
  } catch (Exception ignored) {
   // Corrupt local JSON must not prevent the picker settings above from being read.
  }
  return cursor;
 }

 @Override public Bundle call(String method, String arg, Bundle extras) {
  // The framework call API supplies no Uri; authenticate it by its Binder identity.
  checkCallerOnly();
  if (!("picker_report_v44".equals(method) || "picker_report_v43".equals(method)
      || "picker_report_v42".equals(method) || "picker_report_v40".equals(method)
      || "picker_report_v39".equals(method))) throw new IllegalArgumentException("Unsupported call");
  Context context = getContext();
  if (context == null) throw new IllegalStateException("Provider is not attached");
  context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
      .putLong("picker_last_seen", System.currentTimeMillis())
      .putString("picker_runtime_version", "picker_report_v44".equals(method) ? "0.44"
          : "picker_report_v43".equals(method) ? "0.43" : "picker_report_v42".equals(method) ? "0.42"
          : "picker_report_v40".equals(method) ? "0.40" : "0.39")
      .putString("picker_last_activity", extras != null ? extras.getString("activity", "") : "")
      .apply();
  return new Bundle();
 }

 private void checkCallerOnly() {
  Context context = getContext();
  if (context == null) throw new SecurityException("Provider is not attached");
  int uid = Binder.getCallingUid();
  if (uid == Process.myUid()) return;
  try {
   String caller = getCallingPackage();
   if (caller != null && isTarget(caller)
       && context.getPackageManager().getApplicationInfo(caller, 0).uid == uid) return;
   if (caller == null) {
    String[] packages = context.getPackageManager().getPackagesForUid(uid);
    if (packages != null) for (String name : packages) {
     if (isTarget(name)
         && context.getPackageManager().getApplicationInfo(name, 0).uid == uid) return;
    }
   }
  } catch (PackageManager.NameNotFoundException ignored) {
   // Deny unverifiable callers.
  }
  throw new SecurityException("Caller is not Theme Store");
 }

 @Override public String getType(Uri uri) { check(uri); return "vnd.android.cursor.dir/vnd.phontex.dictionary"; }
 @Override public Uri insert(Uri uri, ContentValues values) { check(uri); throw new UnsupportedOperationException("Read-only provider"); }
 @Override public int delete(Uri uri, String selection, String[] args) { check(uri); throw new UnsupportedOperationException("Read-only provider"); }
 @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { check(uri); throw new UnsupportedOperationException("Read-only provider"); }
}
