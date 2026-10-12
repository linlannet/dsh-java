package net.linlan.dsh.infrastructure.adapter.sandbox.macos;

import net.linlan.dsh.domain.sandbox.adapter.port.ISandboxEnforcerPort;
import net.linlan.dsh.domain.sandbox.model.valobj.SandboxExecutionPolicy;
import net.linlan.dsh.domain.sandbox.model.valobj.SandboxMode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

public class SeatbeltSandbox implements ISandboxEnforcerPort {

    private static final Logger LOGGER = Logger.getLogger(SeatbeltSandbox.class.getName());

    @Override
    public String enforcerName() {
        return "seatbelt";
    }

    @Override
    public boolean isAvailable() {
        return isMacOS() && commandExists("sandbox-exec");
    }

    @Override
    public SandboxEnforceResult enforce(SandboxExecutionPolicy policy, Runnable task) {
        if (policy == null) {
            return SandboxEnforceResult.failure(-1, "policy is null", "none", null);
        }
        if (policy.mode() == SandboxMode.DANGER_FULL_ACCESS) {
            return runTaskDirectly(task, "full-access mode bypasses sandbox", "none(bypass)");
        }
        if (!isAvailable()) {
            return runTaskDirectly(task,
                    "sandbox-exec not available on this platform; running without sandbox",
                    "none");
        }
        String msg = "seatbelt cannot isolate in-JVM Runnable without JNA; running without sandbox";
        LOGGER.warning("[seatbelt] " + msg);
        return runTaskDirectly(task, msg, "seatbelt -> none");
    }

    @Override
    public SandboxEnforceResult enforce(ProcessBuilder builder, SandboxExecutionPolicy policy) {
        if (policy == null) {
            return SandboxEnforceResult.failure(-1, "policy is null", "none", null);
        }
        if (policy.mode() == SandboxMode.DANGER_FULL_ACCESS) {
            return runProcessDirectly(builder, "full-access mode bypasses sandbox", "none(bypass)");
        }
        if (!isAvailable()) {
            return runProcessDirectly(builder,
                    "sandbox-exec not available; running without sandbox",
                    "none");
        }
        Path profileFile = null;
        try {
            profileFile = writeProfileFile(policy);
        } catch (IOException e) {
            LOGGER.warning("[seatbelt] failed to write profile: " + e.getMessage());
            return runProcessDirectly(builder,
                    "failed to write seatbelt profile; running without sandbox",
                    "seatbelt -> none");
        }
        try {
            List<String> cmd = new ArrayList<>();
            cmd.add("sandbox-exec");
            cmd.add("-f");
            cmd.add(profileFile.toString());
            cmd.addAll(builder.command());
            ProcessBuilder pb = new ProcessBuilder(cmd);
            if (builder.directory() != null) {
                pb.directory(builder.directory());
            }
            pb.redirectOutput(builder.redirectOutput());
            pb.redirectError(builder.redirectError());
            Process p = pb.start();
            int code = p.waitFor();
            return new SandboxEnforceResult(code == 0, code,
                    "executed under seatbelt", "seatbelt", null);
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.warning("[seatbelt] sandbox-exec failed: " + e.getMessage());
            return runProcessDirectly(builder,
                    "sandbox-exec failed; running without sandbox",
                    "seatbelt -> none");
        } finally {
            if (profileFile != null) {
                try {
                    Files.deleteIfExists(profileFile);
                } catch (Exception ignored) {
                }
            }
        }
    }

    String generateSeatbeltProfile(SandboxExecutionPolicy policy) {
        StringBuilder sb = new StringBuilder();
        sb.append("(version 1)\n");
        sb.append("(allow default)\n");
        switch (policy.mode()) {
            case READ_ONLY -> {
                sb.append("(deny file-write*)\n");
            }
            case WORKSPACE_WRITE -> {
                List<Path> roots = policy.workspaceRoots();
                if (roots == null || roots.isEmpty()) {
                    roots = policy.workspaceRoot().map(List::of).orElse(List.of());
                }
                sb.append("(deny file-write*)\n");
                for (Path root : roots) {
                    String p = root.toString().replace("\"", "\\\"");
                    sb.append("(allow file-write* (subpath \"")
                            .append(p).append("\"))\n");
                }
            }
            case DANGER_FULL_ACCESS -> {
                sb.append("(allow file-write*)\n");
            }
        }
        return sb.toString();
    }

    private Path writeProfileFile(SandboxExecutionPolicy policy) throws IOException {
        Path file = Files.createTempFile("dsh-seatbelt-", ".sb");
        Files.writeString(file, generateSeatbeltProfile(policy));
        return file;
    }

    private SandboxEnforceResult runTaskDirectly(Runnable task, String message, String fallback) {
        try {
            task.run();
            return SandboxEnforceResult.success(message, fallback);
        } catch (Throwable t) {
            return SandboxEnforceResult.failure(-1,
                    message + "; task threw: " + t.getMessage(), fallback, t);
        }
    }

    private SandboxEnforceResult runProcessDirectly(ProcessBuilder builder, String message, String fallback) {
        try {
            Process p = builder.start();
            int code = p.waitFor();
            return new SandboxEnforceResult(code == 0, code, message, fallback, null);
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            return SandboxEnforceResult.failure(-1,
                    message + "; process failed: " + e.getMessage(), fallback, e);
        }
    }

    private static boolean isMacOS() {
        String os = System.getProperty("os.name", "");
        return os.toLowerCase(Locale.ROOT).contains("mac");
    }

    private static boolean commandExists(String cmd) {
        try {
            Process p = new ProcessBuilder("sh", "-c", "command -v " + cmd)
                    .redirectErrorStream(true).start();
            return p.waitFor() == 0;
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
