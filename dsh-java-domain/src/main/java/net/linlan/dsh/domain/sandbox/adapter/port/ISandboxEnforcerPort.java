package net.linlan.dsh.domain.sandbox.adapter.port;

import net.linlan.dsh.domain.sandbox.model.valobj.SandboxExecutionPolicy;

public interface ISandboxEnforcerPort {

    SandboxEnforceResult enforce(SandboxExecutionPolicy policy, Runnable task);

    SandboxEnforceResult enforce(ProcessBuilder builder, SandboxExecutionPolicy policy);

    boolean isAvailable();

    String enforcerName();

    record SandboxEnforceResult(
            boolean success,
            int exitCode,
            String message,
            String fallbackUsed,
            Throwable error
    ) {
        public static SandboxEnforceResult success(String message, String fallbackUsed) {
            return new SandboxEnforceResult(true, 0, message, fallbackUsed, null);
        }

        public static SandboxEnforceResult failure(int exitCode, String message, String fallbackUsed, Throwable error) {
            return new SandboxEnforceResult(false, exitCode, message, fallbackUsed, error);
        }
    }
}
