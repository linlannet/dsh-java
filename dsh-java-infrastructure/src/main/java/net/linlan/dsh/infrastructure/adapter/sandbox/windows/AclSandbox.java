package net.linlan.dsh.infrastructure.adapter.sandbox.windows;

import net.linlan.dsh.domain.sandbox.adapter.port.ISandboxEnforcerPort;
import net.linlan.dsh.domain.sandbox.model.valobj.SandboxExecutionPolicy;
import net.linlan.dsh.domain.sandbox.model.valobj.SandboxMode;

import java.io.IOException;
import java.util.Locale;
import java.util.logging.Logger;

public class AclSandbox implements ISandboxEnforcerPort {

    private static final Logger LOGGER = Logger.getLogger(AclSandbox.class.getName());

    @Override
    public String enforcerName() {
        return "acl";
    }

    @Override
    public boolean isAvailable() {
        return isWindows();
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
                    "ACL sandbox requires Windows; running without sandbox on non-Windows platform",
                    "none");
        }
        String msg = "ACL sandbox enforcement requires JNA Win32 API which is not loaded; running without sandbox";
        LOGGER.warning("[acl] " + msg);
        return runTaskDirectly(task, msg, "acl -> none");
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
                    "ACL sandbox requires Windows; running without sandbox on non-Windows platform",
                    "none");
        }
        String msg = "ACL sandbox enforcement requires JNA Win32 API which is not loaded; running without sandbox";
        LOGGER.warning("[acl] " + msg);
        return runProcessDirectly(builder, msg, "acl -> none");
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

    private static boolean isWindows() {
        String os = System.getProperty("os.name", "");
        return os.toLowerCase(Locale.ROOT).contains("windows");
    }
}
