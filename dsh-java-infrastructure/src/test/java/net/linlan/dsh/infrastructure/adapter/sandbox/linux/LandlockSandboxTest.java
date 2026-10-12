package net.linlan.dsh.infrastructure.adapter.sandbox.linux;

import net.linlan.dsh.domain.sandbox.adapter.port.ISandboxEnforcerPort;
import net.linlan.dsh.domain.sandbox.model.valobj.SandboxExecutionPolicy;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LandlockSandboxTest {

    private final LandlockSandbox sandbox = new LandlockSandbox();

    @Test
    void enforcerNameIsLandlock() {
        assertEquals("landlock", sandbox.enforcerName());
    }

    @Test
    void nullPolicyReturnsFailure() {
        ISandboxEnforcerPort.SandboxEnforceResult result =
                sandbox.enforce((SandboxExecutionPolicy) null, () -> {});
        assertFalse(result.success());
        assertEquals(-1, result.exitCode());
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
    void readOnlyTaskExecutedWithFallback() {
        SandboxExecutionPolicy policy = SandboxExecutionPolicy.readOnly();
        List<String> trace = new ArrayList<>();
        ISandboxEnforcerPort.SandboxEnforceResult result =
                sandbox.enforce(policy, () -> trace.add("ran"));
        assertTrue(result.success());
        assertEquals(List.of("ran"), trace);
        assertNotNull(result.fallbackUsed());
    }

    @Test
    void taskExceptionPropagatedAsFailure() {
        SandboxExecutionPolicy policy = SandboxExecutionPolicy.readOnly();
        RuntimeException ex = new RuntimeException("boom");
        ISandboxEnforcerPort.SandboxEnforceResult result =
                sandbox.enforce(policy, () -> { throw ex; });
        assertFalse(result.success());
        assertSame(ex, result.error());
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
        SandboxExecutionPolicy policy = null;
        ProcessBuilder pb = new ProcessBuilder("true");
        ISandboxEnforcerPort.SandboxEnforceResult result = sandbox.enforce(pb, policy);
        assertFalse(result.success());
        assertEquals(-1, result.exitCode());
    }

    @Test
    void bwrapArgsBuiltCorrectlyForReadOnly() {
        SandboxExecutionPolicy policy = SandboxExecutionPolicy.readOnly();
        List<String> args = sandbox.buildBwrapArgs(policy, List.of("ls", "-la"));
        assertEquals("bwrap", args.get(0));
        assertTrue(args.contains("--unshare-all"));
        assertTrue(args.contains("--ro-bind"));
        assertEquals("ls", args.get(args.size() - 2));
        assertEquals("-la", args.get(args.size() - 1));
    }

    @Test
    void bwrapArgsBuiltCorrectlyForWorkspaceWrite() {
        SandboxExecutionPolicy policy =
                SandboxExecutionPolicy.workspaceWrite(Path.of("/tmp/ws"));
        List<String> args = sandbox.buildBwrapArgs(policy, List.of("echo", "hi"));
        assertTrue(args.contains("--bind"));
        assertTrue(args.contains("/tmp/ws"));
        assertEquals("echo", args.get(args.size() - 2));
        assertEquals("hi", args.get(args.size() - 1));
    }

    @Test
    void bwrapArgsBuiltCorrectlyForFullAccess() {
        SandboxExecutionPolicy policy = SandboxExecutionPolicy.fullAccess();
        List<String> args = sandbox.buildBwrapArgs(policy, List.of("cat"));
        assertEquals("bwrap", args.get(0));
        assertFalse(args.contains("--ro-bind"));
        assertFalse(args.contains("--bind"));
    }

    @Test
    void readOnlyProcessExecutedWithDegradation() {
        SandboxExecutionPolicy policy = SandboxExecutionPolicy.readOnly();
        ProcessBuilder pb = new ProcessBuilder("true");
        ISandboxEnforcerPort.SandboxEnforceResult result = sandbox.enforce(pb, policy);
        assertTrue(result.success());
        assertEquals(0, result.exitCode());
        assertNotNull(result.fallbackUsed());
    }
}
