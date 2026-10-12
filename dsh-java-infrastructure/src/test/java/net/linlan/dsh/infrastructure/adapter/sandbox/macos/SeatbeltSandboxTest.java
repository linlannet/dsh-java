package net.linlan.dsh.infrastructure.adapter.sandbox.macos;

import net.linlan.dsh.domain.sandbox.adapter.port.ISandboxEnforcerPort;
import net.linlan.dsh.domain.sandbox.model.valobj.SandboxExecutionPolicy;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SeatbeltSandboxTest {

    private final SeatbeltSandbox sandbox = new SeatbeltSandbox();

    @Test
    void enforcerNameIsSeatbelt() {
        assertEquals("seatbelt", sandbox.enforcerName());
    }

    @Test
    void nullPolicyReturnsFailure() {
        ISandboxEnforcerPort.SandboxEnforceResult result =
                sandbox.enforce((SandboxExecutionPolicy) null, () -> {});
        assertFalse(result.success());
        assertEquals("none", result.fallbackUsed());
    }

    @Test
    void fullAccessBypassesSandbox() {
        SandboxExecutionPolicy policy = SandboxExecutionPolicy.fullAccess();
        ISandboxEnforcerPort.SandboxEnforceResult result = sandbox.enforce(policy, () -> {});
        assertTrue(result.success());
        assertEquals("none(bypass)", result.fallbackUsed());
    }

    @Test
    void readOnlyTaskExecuted() {
        SandboxExecutionPolicy policy = SandboxExecutionPolicy.readOnly();
        ISandboxEnforcerPort.SandboxEnforceResult result = sandbox.enforce(policy, () -> {});
        assertTrue(result.success());
    }

    @Test
    void taskExceptionPropagatedAsFailure() {
        SandboxExecutionPolicy policy = SandboxExecutionPolicy.readOnly();
        RuntimeException ex = new RuntimeException("fail");
        ISandboxEnforcerPort.SandboxEnforceResult result =
                sandbox.enforce(policy, () -> { throw ex; });
        assertFalse(result.success());
        assertSame(ex, result.error());
    }

    @Test
    void profileForReadOnlyDeniesWrites() {
        SandboxExecutionPolicy policy = SandboxExecutionPolicy.readOnly();
        String profile = sandbox.generateSeatbeltProfile(policy);
        assertTrue(profile.contains("(version 1)"));
        assertTrue(profile.contains("(deny file-write*)"));
    }

    @Test
    void profileForWorkspaceWriteAllowsRootWrites() {
        Path root = Path.of("/tmp/test-ws");
        SandboxExecutionPolicy policy = SandboxExecutionPolicy.workspaceWrite(root);
        String profile = sandbox.generateSeatbeltProfile(policy);
        assertTrue(profile.contains("(deny file-write*)"));
        assertTrue(profile.contains("(allow file-write*"));
        assertTrue(profile.contains("/tmp/test-ws"));
    }

    @Test
    void profileForFullAccessAllowsAll() {
        SandboxExecutionPolicy policy = SandboxExecutionPolicy.fullAccess();
        String profile = sandbox.generateSeatbeltProfile(policy);
        assertTrue(profile.contains("(allow file-write*)"));
    }

    @Test
    void fullAccessProcessBypassesSandbox() {
        SandboxExecutionPolicy policy = SandboxExecutionPolicy.fullAccess();
        ProcessBuilder pb = new ProcessBuilder("true");
        ISandboxEnforcerPort.SandboxEnforceResult result = sandbox.enforce(pb, policy);
        assertTrue(result.success());
        assertEquals(0, result.exitCode());
    }

    @Test
    void nullPolicyProcessReturnsFailure() {
        ProcessBuilder pb = new ProcessBuilder("true");
        ISandboxEnforcerPort.SandboxEnforceResult result =
                sandbox.enforce(pb, null);
        assertFalse(result.success());
        assertEquals(-1, result.exitCode());
    }

    @Test
    void readOnlyProcessExecutedWithDegradation() {
        SandboxExecutionPolicy policy = SandboxExecutionPolicy.readOnly();
        ProcessBuilder pb = new ProcessBuilder("true");
        ISandboxEnforcerPort.SandboxEnforceResult result = sandbox.enforce(pb, policy);
        assertTrue(result.success());
        assertEquals(0, result.exitCode());
    }
}
