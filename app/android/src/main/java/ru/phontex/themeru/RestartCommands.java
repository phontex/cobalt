package ru.phontex.themeru;

import java.util.regex.Pattern;

/** Command construction is kept separate so it can be tested without invoking su. */
public final class RestartCommands {
    private static final Pattern PACKAGE = Pattern.compile("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+");
    private RestartCommands() {}
    public static String forceStop(String packageName) {
        if (packageName == null || packageName.length() > 255 || !PACKAGE.matcher(packageName).matches())
            throw new IllegalArgumentException("Invalid package name");
        // Root actions are limited to the only supported profile.
        if (!packageName.equals("com.heytap.themestore"))
            throw new IllegalArgumentException("Unsupported package");
        return "am force-stop " + packageName;
    }
    public static String reboot() { return "svc power reboot"; }
}
