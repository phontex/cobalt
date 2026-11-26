package com.cobalt.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * LSPosed entry point. Runs inside the target application and replaces displayed text according
 * to the {@link TranslationPolicy} loaded from Cobalt's dictionary provider.
 *
 * The hook does not depend on the version of the target application: any build is handled the
 * same way, and any failure is logged and swallowed so the target never crashes because of us.
 */
public final class Hook implements IXposedHookLoadPackage {
    static final String TAG = "cobalt_picker_v1";
    static final Uri URI = Uri.parse("content://com.cobalt.app.dictionary/entries");

    /** Longest text that is considered a UI label; anything longer is left untouched. */
    private static final int MAX_TEXT_LENGTH = 4096;
    /** The picker button is re-checked once after the target finished configuring its window. */
    private static final long PICKER_RECHECK_DELAY_MS = 750;
    private static final int PICKER_SIZE_DP = 52;

    static volatile TranslationPolicy translations = TranslationPolicy.disabled();
    static volatile boolean loaded = false;
    private static volatile long lastLoadFailedAt = 0L;
    static volatile boolean picker = false;
    static volatile boolean autoHide = false;

    /** Source text of views whose text we replaced, so the picker can show the original. */
    static final Map<TextView, String> originals = Collections.synchronizedMap(new WeakHashMap<TextView, String>());
    /** Activities currently waiting for the user to touch a label. */
    static final Map<Activity, Boolean> choosing = Collections.synchronizedMap(new WeakHashMap<Activity, Boolean>());
    private static final Map<Activity, Integer> pickerGenerations = Collections.synchronizedMap(new WeakHashMap<Activity, Integer>());

    // ------------------------------------------------------------------ settings

    private static boolean shouldLoad() {
        if (loaded) return false;
        long failedAt = lastLoadFailedAt;
        return failedAt == 0L || (android.os.SystemClock.uptimeMillis() - failedAt) > 2000L;
    }

    static void load(Context context) {
        try (Cursor cursor = context.getContentResolver().query(URI, null, null, null, null)) {
            if (cursor == null) throw new IllegalStateException("Dictionary provider returned no data");
            Map<String, String> custom = new HashMap<>();
            String template = "none";
            boolean pickerEnabled = false;
            boolean hide = false;
            while (cursor.moveToNext()) {
                String key = cursor.getString(0);
                String value = cursor.getString(1);
                if (key == null) pickerEnabled = "1".equals(value);
                else if ("\u0001cobalt_picker_hide".equals(key)) hide = "1".equals(value);
                else if ("\u0001cobalt_template".equals(key)) template = value;
                else custom.put(key, value);
            }
            translations = new TranslationPolicy(template, custom);
            picker = pickerEnabled;
            autoHide = hide;
            loaded = true;
            lastLoadFailedAt = 0L;
        } catch (Throwable e) {
            // Fail closed, but allow retry after cooldown if provider was not ready at boot.
            translations = TranslationPolicy.disabled();
            picker = false;
            autoHide = false;
            lastLoadFailedAt = android.os.SystemClock.uptimeMillis();
            XposedBridge.log("Cobalt: cannot load settings: " + e);
        }
    }

    // ------------------------------------------------------------------ installation

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam pkg) {
        if (!"com.heytap.themestore".equals(pkg.packageName) && !"com.oplus.themestore".equals(pkg.packageName)) return;

        hookTextSetters();
        hookPickerLifecycle();
        hookPickerTouch();
    }

    private static void hookTextSetters() {
        try {
            XposedHelpers.findAndHookMethod(TextView.class, "setText", CharSequence.class, TextView.BufferType.class, new TextHook());
        } catch (Throwable e) {
            XposedBridge.log("Cobalt: " + e);
        }
        try {
            XposedHelpers.findAndHookMethod(TextView.class, "setHint", CharSequence.class, new HintHook());
        } catch (Throwable e) {
            XposedBridge.log("Cobalt hint: " + e);
        }
    }

    private static void hookPickerLifecycle() {
        try {
            XposedHelpers.findAndHookMethod(Activity.class, "onResume", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        Activity activity = (Activity) param.thisObject;
                        load(activity);
                        ViewGroup root = (ViewGroup) activity.getWindow().getDecorView();
                        int generation = nextGeneration(activity);
                        // Base Activity.onResume can run before the target's override finishes configuring
                        // its window. Install after the full lifecycle callback, then do one bounded
                        // re-check in case the button was replaced.
                        root.post(() -> {
                            if (isCurrent(activity, generation)) ensurePicker(activity, root);
                        });
                        root.postDelayed(() -> {
                            if (isCurrent(activity, generation)) ensurePicker(activity, root);
                        }, PICKER_RECHECK_DELAY_MS);
                    } catch (Throwable e) {
                        XposedBridge.log("Cobalt picker resume: " + e);
                    }
                }
            });
        } catch (Throwable e) {
            XposedBridge.log("Cobalt picker resume hook: " + e);
        }
        try {
            XposedHelpers.findAndHookMethod(Activity.class, "onPause", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    Activity activity = (Activity) param.thisObject;
                    nextGeneration(activity);
                    choosing.remove(activity);
                }
            });
        } catch (Throwable e) {
            XposedBridge.log("Cobalt picker pause hook: " + e);
        }
    }

    private static void hookPickerTouch() {
        try {
            XposedHelpers.findAndHookMethod(Activity.class, "dispatchTouchEvent", MotionEvent.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        Activity activity = (Activity) param.thisObject;
                        if (!Boolean.TRUE.equals(choosing.get(activity))) return;
                        handleSelectionTouch(activity, (MotionEvent) param.args[0]);
                        // While choosing, the target application must not see any touch.
                        param.setResult(true);
                    } catch (Throwable e) {
                        choosing.remove((Activity) param.thisObject);
                        XposedBridge.log("Cobalt selection: " + e);
                    }
                }
            });
        } catch (Throwable e) {
            XposedBridge.log("Cobalt touch: " + e);
        }
    }

    // ------------------------------------------------------------------ text replacement

    /** Replaces text passed to {@code TextView.setText}. */
    private static final class TextHook extends XC_MethodHook {
        @Override
        protected void beforeHookedMethod(MethodHookParam param) {
            try {
                if (!(param.thisObject instanceof TextView) || param.thisObject instanceof EditText) return;
                TextView view = (TextView) param.thisObject;
                if (shouldLoad()) load(view.getContext());
                if (!(param.args[0] instanceof CharSequence)) {
                    originals.remove(view);
                    return;
                }
                CharSequence raw = (CharSequence) param.args[0];
                String input = raw.toString();
                if (input.length() == 0 || input.length() > MAX_TEXT_LENGTH) {
                    originals.remove(view);
                    return;
                }
                TranslationPolicy active = translations;
                String output = active.translate(input);
                rememberOriginal(view, input, active);
                if (output != null) param.args[0] = preserveSpans(raw, output);
            } catch (Throwable e) {
                XposedBridge.log("Cobalt text: " + e);
            }
        }
    }

    /** Replaces text passed to {@code TextView.setHint}. */
    private static final class HintHook extends XC_MethodHook {
        @Override
        protected void beforeHookedMethod(MethodHookParam param) {
            try {
                if (!(param.thisObject instanceof TextView) || param.thisObject instanceof EditText) return;
                if (!(param.args[0] instanceof CharSequence)) return;
                CharSequence raw = (CharSequence) param.args[0];
                String input = raw.toString();
                if (input.length() == 0 || input.length() > MAX_TEXT_LENGTH) return;
                if (shouldLoad()) load(((TextView) param.thisObject).getContext());
                String output = translations.translate(input);
                if (output != null) param.args[0] = preserveSpans(raw, output);
            } catch (Throwable e) {
                XposedBridge.log("Cobalt hint: " + e);
            }
        }
    }

    /** Preserves whole-string font/style/color/size spans when replacing a Spanned CharSequence. */
    private static CharSequence preserveSpans(CharSequence raw, String output) {
        if (!(raw instanceof android.text.Spanned) || raw.length() == 0) return output;
        android.text.Spanned src = (android.text.Spanned) raw;
        Object[] spans = src.getSpans(0, raw.length(), Object.class);
        if (spans == null || spans.length == 0) return output;
        android.text.SpannableString dst = new android.text.SpannableString(output);
        for (Object span : spans) {
            if (src.getSpanStart(span) == 0 && src.getSpanEnd(span) == raw.length()) {
                dst.setSpan(span, 0, output.length(), src.getSpanFlags(span));
            }
        }
        return dst;
    }

    /**
     * Keeps the source text of a view for the picker. A view that is later set to some other text
     * (not the translation of what we remembered) forgets it.
     */
    private static void rememberOriginal(TextView view, String input, TranslationPolicy active) {
        if (picker && containsChinese(input)) {
            originals.put(view, input);
            return;
        }
        String remembered = originals.get(view);
        String rememberedTranslation = remembered == null ? null : active.translate(remembered);
        if (!picker || !input.equals(rememberedTranslation)) originals.remove(view);
    }

    // ------------------------------------------------------------------ picker

    /** Consumes one touch while the user is choosing a label to translate. */
    private static void handleSelectionTouch(Activity activity, MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_CANCEL) {
            choosing.remove(activity);
            return;
        }
        if (action != MotionEvent.ACTION_UP) return;

        choosing.remove(activity);
        TextView label = find(activity.getWindow().getDecorView(), (int) event.getRawX(), (int) event.getRawY());
        if (label == null) {
            Toast.makeText(activity, "Текст не найден", Toast.LENGTH_SHORT).show();
            return;
        }
        String shown = label.getText().toString();
        String original = originals.get(label);
        if (original == null && containsChinese(shown)) original = shown;
        if (original == null) {
            Toast.makeText(activity, "Исходный текст недоступен. Переоткройте экран.", Toast.LENGTH_LONG).show();
            return;
        }
        Intent intent = new Intent();
        intent.setClassName("com.cobalt.app", "com.cobalt.app.MainActivity");
        intent.putExtra("original", original);
        intent.putExtra("translated", shown);
        activity.startActivity(intent);
    }

    static int nextGeneration(Activity activity) {
        synchronized (pickerGenerations) {
            Integer current = pickerGenerations.get(activity);
            int next = current == null ? 1 : current + 1;
            pickerGenerations.put(activity, next);
            return next;
        }
    }

    static boolean isCurrent(Activity activity, int generation) {
        synchronized (pickerGenerations) {
            return !activity.isFinishing() && !activity.isDestroyed()
                    && Integer.valueOf(generation).equals(pickerGenerations.get(activity));
        }
    }

    static void ensurePicker(Activity activity, ViewGroup root) {
        try {
            View existing = root.findViewWithTag(TAG);
            if (!picker) {
                removeFromParent(existing);
                return;
            }
            if (existing instanceof PickerButton && existing.getParent() == root) return;
            removeFromParent(existing);

            PickerButton button = new PickerButton(activity, root, autoHide, () -> {
                choosing.put(activity, true);
                Toast.makeText(activity, "Коснитесь надписи для перевода", Toast.LENGTH_SHORT).show();
            });
            button.setTag(TAG);
            int size = (int) (PICKER_SIZE_DP * activity.getResources().getDisplayMetrics().density);
            root.addView(button, new FrameLayout.LayoutParams(size, size, Gravity.TOP | Gravity.START));
            button.initialize();
            reportPicker(activity);
        } catch (Throwable e) {
            XposedBridge.log("Cobalt picker install: " + e);
        }
    }

    private static void reportPicker(Activity activity) {
        try {
            Bundle report = new Bundle();
            report.putString("activity", activity.getClass().getName());
            activity.getContentResolver().call(URI, "picker_report_v44", null, report);
        } catch (Throwable e) {
            XposedBridge.log("Cobalt picker report: " + e);
        }
    }

    private static void removeFromParent(View view) {
        if (view != null && view.getParent() instanceof ViewGroup) {
            ((ViewGroup) view.getParent()).removeView(view);
        }
    }

    // ------------------------------------------------------------------ helpers

    static boolean containsChinese(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 0x3400 && c <= 0x9fff) return true;
        }
        return false;
    }

    /** Topmost visible label under the given screen point that we can offer for translation. */
    static TextView find(View view, int x, int y) {
        if (view.getVisibility() != View.VISIBLE || view.getAlpha() == 0) return null;
        Rect bounds = new Rect();
        if (!view.getGlobalVisibleRect(bounds) || !bounds.contains(x, y)) return null;

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = group.getChildCount() - 1; i >= 0; i--) {
                TextView found = find(group.getChildAt(i), x, y);
                if (found != null) return found;
            }
        }
        boolean isLabel = view instanceof TextView && !(view instanceof EditText) && !TAG.equals(view.getTag());
        if (isLabel && (originals.containsKey(view) || containsChinese(((TextView) view).getText().toString()))) {
            return (TextView) view;
        }
        return null;
    }
}