package net.linlan.dsh.domain.task.permission;

import java.util.List;
import java.util.Objects;

public final class PermissionPreset {

    public static final String PRESET_FULL_ACCESS = "full_access";
    public static final String PRESET_READ_ONLY = "read_only";
    public static final String PRESET_SAFE = "safe";

    public record PermissionConfig(
            List<String> allowedTools,
            List<String> deniedTools,
            List<String> allowedPaths,
            List<String> deniedPaths
    ) {
        public PermissionConfig {
            Objects.requireNonNull(allowedTools, "allowedTools");
            Objects.requireNonNull(deniedTools, "deniedTools");
            Objects.requireNonNull(allowedPaths, "allowedPaths");
            Objects.requireNonNull(deniedPaths, "deniedPaths");
        }

        public static PermissionConfig empty() {
            return new PermissionConfig(List.of(), List.of(), List.of(), List.of());
        }
    }

    public record ApplyResult(String presetName, PermissionConfig config) {}

    public ApplyResult apply(String presetName, PermissionConfig config) {
        if (presetName == null) {
            throw new IllegalArgumentException("presetName must not be null");
        }
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        return switch (presetName) {
            case PRESET_FULL_ACCESS -> new ApplyResult(presetName, new PermissionConfig(
                    List.of("*"),
                    List.of(),
                    List.of("*"),
                    List.of()
            ));
            case PRESET_READ_ONLY -> new ApplyResult(presetName, new PermissionConfig(
                    List.of("read", "list", "search", "glob", "grep"),
                    List.of("write", "edit", "delete", "exec", "shell"),
                    config.allowedPaths(),
                    List.of()
            ));
            case PRESET_SAFE -> new ApplyResult(presetName, new PermissionConfig(
                    List.of("read", "list", "search", "glob", "grep", "edit"),
                    List.of("exec", "shell", "delete"),
                    config.allowedPaths().isEmpty() ? List.of("./**") : config.allowedPaths(),
                    List.of("/etc/**", "/root/**", "~/.ssh/**")
            ));
            default -> throw new IllegalArgumentException("Unknown preset: " + presetName);
        };
    }

    public boolean isKnownPreset(String presetName) {
        return PRESET_FULL_ACCESS.equals(presetName)
                || PRESET_READ_ONLY.equals(presetName)
                || PRESET_SAFE.equals(presetName);
    }
}
