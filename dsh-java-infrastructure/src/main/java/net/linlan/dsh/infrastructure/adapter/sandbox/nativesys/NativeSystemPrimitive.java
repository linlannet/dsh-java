package net.linlan.dsh.infrastructure.adapter.sandbox.nativesys;

import java.lang.management.ManagementFactory;
import java.util.Locale;

public final class NativeSystemPrimitive {

    private final String osName;
    private final boolean linux;
    private final boolean mac;
    private final boolean windows;

    public NativeSystemPrimitive() {
        this(System.getProperty("os.name", ""));
    }

    NativeSystemPrimitive(String osName) {
        this.osName = osName == null ? "" : osName;
        String lower = this.osName.toLowerCase(Locale.ROOT);
        this.linux = lower.contains("linux");
        this.mac = lower.contains("mac") || lower.contains("darwin");
        this.windows = lower.contains("win");
    }

    public String osName() {
        return osName;
    }

    public boolean isLinux() {
        return linux;
    }

    public boolean isMac() {
        return mac;
    }

    public boolean isWindows() {
        return windows;
    }

    public long currentPid() {
        String name = ManagementFactory.getRuntimeMXBean().getName();
        int at = name.indexOf('@');
        if (at <= 0) {
            return -1L;
        }
        try {
            return Long.parseLong(name.substring(0, at));
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    public boolean supportsLandlock() {
        return linux;
    }

    public boolean supportsFlock() {
        return linux || mac;
    }
}
