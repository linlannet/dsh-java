package net.linlan.dsh.infrastructure.adapter.sandbox.linux;

import net.linlan.dsh.domain.sandbox.adapter.port.ISandboxEnforcerPort;
import net.linlan.dsh.domain.sandbox.model.valobj.SandboxExecutionPolicy;
import net.linlan.dsh.domain.sandbox.model.valobj.SandboxMode;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

public class LandlockSandbox implements ISandboxEnforcerPort {

    private static final Logger LOGGER = Logger.getLogger(LandlockSandbox.class.getName());

    @Override
    public String enforcerName() {
        return "landlock";
    }

    @Override
    public boolean isAvailable() {
        return isLinux() && (landlockSupported() || bwrapAvailable());
    }

    @Override
    public SandboxEnforceResult enforce(SandboxExecutionPolicy policy, Runnable task) {
        if (policy == null) {
            return SandboxEnforceResult.failure(-1, "policy is null", "none", null);
        }
        if (policy.mode() == SandboxMode.DANGER_FULL_ACCESS) {
            return runTaskDirectly(task, "full-access mode bypasses sandbox", "none(bypass)");
        }
        List<String> attempted = new ArrayList<>();
        if (isLinux() && landlockSupported()) {
            attempted.add("landlock");
        }
        if (isLinux() && bwrapAvailable()) {
            attempted.add("bwrap");
        }
        if (attempted.isEmpty()) {
            return runTaskDirectly(task,
                    "Landlock/bwrap not available on this platform; running without sandbox",
                    "none");
        }
        String chain = String.join(" -> ", attempted) + " -> none";
        String msg = "Landlock/bwrap cannot isolate in-JVM Runnable; attempted chain: "
                + chain + "; running without sandbox";
        LOGGER.warning("[landlock] " + msg);
        return runTaskDirectly(task, msg, chain);
    }

    @Override
    public SandboxEnforceResult enforce(ProcessBuilder builder, SandboxExecutionPolicy policy) {
        if (policy == null) {
            return SandboxEnforceResult.failure(-1, "policy is null", "none", null);
        }
        if (policy.mode() == SandboxMode.DANGER_FULL_ACCESS) {
            return runProcessDirectly(builder, "full-access mode bypasses sandbox", "none(bypass)");
        }
        if (isLinux() && bwrapAvailable()) {
            SandboxEnforceResult bwrapResult = runWithBwrap(builder, policy);
            if (bwrapResult.success()) {
                return bwrapResult;
            }
            LOGGER.warning("[landlock] bwrap failed: " + bwrapResult.message()
                    + "; falling back to direct execution");
            return runProcessDirectly(builder,
                    "bwrap failed, running without sandbox",
                    "bwrap -> none");
        }
        if (isLinux() && landlockSupported()) {
            LOGGER.warning("[landlock] landlock cannot wrap external process without helper; "
                    + "falling back to direct execution");
            return runProcessDirectly(builder,
                    "landlock cannot wrap external process without helper; running without sandbox",
                    "landlock -> none");
        }
        return runProcessDirectly(builder,
                "Landlock/bwrap not available; running without sandbox",
                "none");
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

    private SandboxEnforceResult runWithBwrap(ProcessBuilder builder, SandboxExecutionPolicy policy) {
        List<String> bwrapArgs = buildBwrapArgs(policy, builder.command());
        ProcessBuilder pb = new ProcessBuilder(bwrapArgs);
        if (builder.directory() != null) {
            pb.directory(builder.directory());
        }
        pb.redirectOutput(builder.redirectOutput());
        pb.redirectError(builder.redirectError());
        try {
            Process p = pb.start();
            int code = p.waitFor();
            return new SandboxEnforceResult(code == 0, code,
                    "executed under bwrap", "bwrap", null);
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            return SandboxEnforceResult.failure(-1,
                    "bwrap execution failed: " + e.getMessage(), "bwrap", e);
        }
    }

    List<String> buildBwrapArgs(SandboxExecutionPolicy policy, List<String> originalCommand) {
        List<String> args = new ArrayList<>();
        args.add("bwrap");
        args.add("--unshare-all");
        args.add("--share-net");
        args.add("--die-with-parent");
        switch (policy.mode()) {
            case READ_ONLY -> {
                args.add("--ro-bind");
                args.add("/");
                args.add("/");
            }
            case WORKSPACE_WRITE -> {
                args.add("--ro-bind");
                args.add("/");
                args.add("/");
                List<Path> roots = policy.workspaceRoots();
                if (roots == null || roots.isEmpty()) {
                    roots = policy.workspaceRoot().map(List::of).orElse(List.of());
                }
                for (Path root : roots) {
                    args.add("--bind");
                    args.add(root.toString());
                    args.add(root.toString());
                }
            }
            case DANGER_FULL_ACCESS -> {
            }
        }
        args.addAll(originalCommand);
        return args;
    }

    private static boolean isLinux() {
        String os = System.getProperty("os.name", "");
        return os.toLowerCase(Locale.ROOT).contains("linux");
    }

    private static boolean landlockSupported() {
        try {
            return java.nio.file.Files.exists(
                    java.nio.file.Path.of("/proc/sys/kernel/landlock_restrict"));
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean bwrapAvailable() {
        return commandExists("bwrap");
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
