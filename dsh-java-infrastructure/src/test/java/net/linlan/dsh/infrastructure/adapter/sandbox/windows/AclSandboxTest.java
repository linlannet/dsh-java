package net.linlan.dsh.infrastructure.adapter.sandbox.windows;

import net.linlan.dsh.domain.sandbox.adapter.port.ISandboxEnforcerPort;
import net.linlan.dsh.domain.sandbox.model.valobj.SandboxExecutionPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AclSandboxTest {

    private final AclSandbox sandbox = new AclSandbox();

    @Test
    void enforcerNameIsAcl() {
        assertEquals("acl", sandbox.enforcerName());
    }

    @Test
    void notAvailableOnNonWindows() {
        String os = System.getProperty("os.name", "");
        if (!os.toLowerCase().contains("windows")) {
            assertFalse(sandbox.isAvailable());
        }
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
    void readOnlyTaskExecutedWithDegradation() {
        SandboxExecutionPolicy policy = SandboxExecutionPolicy.readOnly();
        ISandboxEnforcerPort.SandboxEnforceResult result = sandbox.enforce(policy, () -> {});
        assertTrue(result.success());
        assertNotNull(result.fallbackUsed());
        assertTrue(result.message().contains("without sandbox")
                || result.message().contains("bypasses"));
    }

    @Test
    void taskExceptionPropagatedAsFailure() {
        SandboxExecutionPolicy policy = SandboxExecutionPolicy.readOnly();
        RuntimeException ex = new RuntimeException("err");
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
        ProcessBuilder pb = new ProcessBuilder("true");
        ISandboxEnforcerPort.SandboxEnforceResult result = sandbox.enforce(pb, null);
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
