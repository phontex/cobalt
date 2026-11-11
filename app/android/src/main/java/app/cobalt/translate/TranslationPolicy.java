package app.cobalt.translate;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
/** Immutable selection for one app profile. Unknown templates are disabled. */
public final class TranslationPolicy {
    private final String template;
    private final Map<String, String> custom;
    public TranslationPolicy(String template, Map<String, String> custom) {
        this.template = template;
        this.custom = Collections.unmodifiableMap(new HashMap<>(custom));
    }
    public static TranslationPolicy disabled() {
        return new TranslationPolicy("none", Collections.emptyMap());
    }
    /** A null result means the original text must be left unchanged. */
    public String translate(String original) {
        if (!"ru".equals(template) || original == null) return null;
        String value = custom.get(original);
        return value != null ? value : Dictionary.translate(original);
    }
}
