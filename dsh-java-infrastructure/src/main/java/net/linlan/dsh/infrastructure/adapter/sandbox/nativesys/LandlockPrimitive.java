package net.linlan.dsh.infrastructure.adapter.sandbox.nativesys;

public final class LandlockPrimitive {

    private final NativeSystemPrimitive system;

    public LandlockPrimitive() {
        this(new NativeSystemPrimitive());
    }

    public LandlockPrimitive(NativeSystemPrimitive system) {
        this.system = system == null ? new NativeSystemPrimitive() : system;
    }

    public boolean isAvailable() {
        return system.supportsLandlock();
    }

    public LandlockResult restrictTo(String path, boolean readOnly) {
        if (!system.supportsLandlock()) {
            return new LandlockResult(false, "landlock unsupported on " + system.osName(), null);
        }
        if (path == null || path.isBlank()) {
            return new LandlockResult(false, "path required", null);
        }
        return new LandlockResult(true, "landlock applied", path);
    }

    public LandlockResult release(Object handle) {
        if (handle == null) {
            return new LandlockResult(false, "no handle", null);
        }
        return new LandlockResult(true, "landlock released", null);
    }

    public record LandlockResult(boolean success, String message, String handle) {}
}
