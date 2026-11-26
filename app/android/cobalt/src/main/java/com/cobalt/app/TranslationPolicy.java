package com.cobalt.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Immutable translation policy for one target process.
 *
 * Lookup has three steps: exact source text, a normalized source (spaces/case/placeholders), and
 * finally a bounded phrase rebuild for composed server sentences.
 *
 * Performance is part of correctness here. Every TextView.setText runs through this class on the
 * target application's main thread, so the phrase rebuild is limited to Chinese text long enough
 * to be a sentence and stops after a few replacements. Results are memoised because the same
 * label is set repeatedly while scrolling.
 */
public final class TranslationPolicy {
    private static final Pattern PLACEHOLDER = Pattern.compile("%(?:\\d+\\$)?[-+#0 ]*(?:\\d+(?:\\.\\d+)?)?[a-zA-Z]");
    private static final Pattern SPACES = Pattern.compile("\\s+");
    /** Above this many characters the view text is not a UI label worth scanning. */
    private static final int MAX_COMPOSED_LENGTH = 512;
    /** Composed server sentences are only rebuilt when the phrase clearly dominates the text. */
    private static final int MIN_COMPOSED_LENGTH = 12;
    private static final int MIN_FRAGMENT_LENGTH = 4;
    private static final int MAX_FRAGMENT_REPLACEMENTS = 3;
    private static final int CACHE_LIMIT = 2048;

    private final String template;
    private final Map<String, String> custom;
    private final Map<String, String> normalized;
    private final String[] fragmentSource;
    private final String[] fragmentTarget;
    private final Map<String, String> cache = new LinkedHashMap<String, String>(256, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            return size() > CACHE_LIMIT;
        }
    };

    public TranslationPolicy(String template, Map<String, String> custom) {
        this.template = template;
        this.custom = Collections.unmodifiableMap(new LinkedHashMap<>(custom));
        Map<String, String> normalizedCopy = new HashMap<>();
        List<Map.Entry<String, String>> candidates = new ArrayList<>(custom.size());
        for (Map.Entry<String, String> entry : custom.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || entry.getValue().isEmpty()) continue;
            String key = entry.getKey();
            normalizedCopy.putIfAbsent(normalize(key), entry.getValue());
            // Only sentences are useful for composed replacement, and only when the source phrase is
            // long enough that a match cannot be an accidental fragment of a product name.
            if (key.length() >= MIN_FRAGMENT_LENGTH && !key.startsWith("\u0001cobalt_")) {
                candidates.add(entry);
            }
        }
        normalized = Collections.unmodifiableMap(normalizedCopy);
        candidates.sort(Comparator.comparingInt((Map.Entry<String, String> entry) -> entry.getKey().length()).reversed());
        fragmentSource = new String[candidates.size()];
        fragmentTarget = new String[candidates.size()];
        for (int i = 0; i < candidates.size(); i++) {
            fragmentSource[i] = candidates.get(i).getKey().toLowerCase(Locale.ROOT);
            fragmentTarget[i] = candidates.get(i).getValue();
        }
    }

    public static TranslationPolicy disabled() {
        return new TranslationPolicy("none", Collections.emptyMap());
    }

    /** A null result means the original text must be left unchanged. */
    public String translate(String original) {
        if (!"ru".equalsIgnoreCase(template) || original == null || original.isEmpty()) return null;
        if (original.length() > MAX_COMPOSED_LENGTH) return null;

        synchronized (cache) {
            if (cache.containsKey(original)) return cache.get(original);
        }

        String result = resolve(original);

        synchronized (cache) {
            cache.put(original, result);
        }
        return result;
    }

    private String resolve(String original) {
        String exact = custom.get(original);
        if (exact != null) return exact;

        String normalizedValue = normalized.get(normalize(original));
        if (normalizedValue != null) return adaptPlaceholders(normalizedValue, original);

        // Fragments can only exist inside Chinese source text. Skipping every other string keeps the
        // hook cheap: previously each label scanned the whole dictionary, which stalled the UI.
        if (!hasIdeographs(original) || original.length() < MIN_COMPOSED_LENGTH) {
            return Dictionary.translate(original);
        }
        String composed = replaceFragments(original);
        if (composed != null && !composed.equals(original)) return composed;
        return Dictionary.translate(original);
    }

    private static boolean hasIdeographs(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= 0x3400 && c <= 0x9fff) return true;
        }
        return false;
    }

    private String replaceFragments(String input) {
        String lower = input.toLowerCase(Locale.ROOT);
        StringBuilder result = null;
        int replaced = 0;
        for (int i = 0; i < fragmentSource.length && replaced < MAX_FRAGMENT_REPLACEMENTS; i++) {
            String source = fragmentSource[i];
            if (source.length() < MIN_FRAGMENT_LENGTH) break;
            if (source.length() * 3 < input.length()) break;
            int index = lower.indexOf(source);
            if (index < 0) continue;
            if (result == null) result = new StringBuilder(input);
            result.replace(index, index + source.length(), fragmentTarget[i]);
            lower = result.toString().toLowerCase(Locale.ROOT);
            replaced++;
        }
        return replaced == 0 ? null : result.toString();
    }

    private static String adaptPlaceholders(String translation, String original) {
        Matcher translated = PLACEHOLDER.matcher(translation);
        Matcher source = PLACEHOLDER.matcher(original);
        List<String> sourceTokens = new ArrayList<>();
        while (source.find()) sourceTokens.add(source.group());
        if (sourceTokens.isEmpty()) return translation;
        StringBuffer output = new StringBuffer();
        int index = 0;
        while (translated.find()) {
            String replacement = index < sourceTokens.size() ? sourceTokens.get(index) : translated.group();
            translated.appendReplacement(output, Matcher.quoteReplacement(replacement));
            index++;
        }
        translated.appendTail(output);
        return output.toString();
    }

    private static String normalize(String value) {
        String collapsed = SPACES.matcher(value.trim()).replaceAll(" ");
        String masked = PLACEHOLDER.matcher(collapsed).replaceAll("{cobalt_placeholder}");
        return masked.toLowerCase(Locale.ROOT);
    }
}
