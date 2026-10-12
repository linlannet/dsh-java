package net.linlan.dsh.infrastructure.adapter.sandbox.windows;

import java.nio.file.AccessDeniedException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Locale;

public class AclDiagnostic {

    public record AclDiagnosticResult(String rejectedPath, String reason, String suggestion) {}

    public AclDiagnosticResult diagnose(String path, Throwable error) {
        String normalizedPath = normalizePath(path);
        if (error == null) {
            return new AclDiagnosticResult(normalizedPath, "no error", "no action needed");
        }
        String reason = determineReason(error);
        String suggestion = suggestFix(reason, normalizedPath);
        return new AclDiagnosticResult(normalizedPath, reason, suggestion);
    }

    private String determineReason(Throwable error) {
        if (error instanceof AccessDeniedException) {
            return "access denied by ACL";
        }
        if (error instanceof NoSuchFileException) {
            return "path does not exist";
        }
        if (error instanceof SecurityException) {
            return "security manager denied access";
        }
        String msg = error.getMessage() != null
                ? error.getMessage().toLowerCase(Locale.ROOT) : "";
        if (msg.contains("access is denied") || msg.contains("permission denied")
                || msg.contains("拒绝访问") || msg.contains("权限不足")) {
            return "access denied by ACL";
        }
        if (msg.contains("not found") || msg.contains("no such file") || msg.contains("找不到")) {
            return "path does not exist";
        }
        if (msg.contains("read-only") || msg.contains("只读")) {
            return "path is read-only";
        }
        if (msg.contains("disk full") || msg.contains("space") || msg.contains("磁盘空间不足")) {
            return "disk space insufficient";
        }
        return "unknown error: " + error.getClass().getSimpleName();
    }

    private String suggestFix(String reason, String path) {
        return switch (reason) {
            case "access denied by ACL" ->
                    "grant write ACL on '" + path + "' or add the path to sandbox writable roots";
            case "path does not exist" ->
                    "create the target path '" + path + "' or verify it is correct";
            case "security manager denied access" ->
                    "check Java SecurityManager policy for '" + path + "'";
            case "path is read-only" ->
                    "remount or change attributes of '" + path + "' to allow writes";
            case "disk space insufficient" ->
                    "free disk space on the volume containing '" + path + "'";
            case "no error" -> "no action needed";
            default ->
                    "inspect the underlying error and verify sandbox configuration for '" + path + "'";
        };
    }

    private String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "<unknown>";
        }
        try {
            return Path.of(path).toAbsolutePath().normalize().toString();
        } catch (Exception e) {
            return path;
        }
    }
}
