package net.linlan.dsh.domain.typert.ptc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class PtcSandboxNodeProcessTest {

    @Test
    void shouldCreateWithValidParameters() {
        PtcSandboxNodeProcess process = new PtcSandboxNodeProcess("node", "/sandbox.js");

        assertEquals("node", process.nodeExecutable());
        assertEquals("/sandbox.js", process.sandboxScriptPath());
        assertEquals(PtcSandboxNodeProcess.State.CREATED, process.state());
    }

    @Test
    void shouldRejectBlankNodeExecutable() {
        assertThrows(IllegalArgumentException.class, () ->
                new PtcSandboxNodeProcess("  ", "/sandbox.js"));
    }

    @Test
    void shouldRejectBlankScriptPath() {
        assertThrows(IllegalArgumentException.class, () ->
                new PtcSandboxNodeProcess("node", "  "));
    }

    @Test
    void shouldTransitionToRunningOnStart() {
        PtcSandboxNodeProcess process = new PtcSandboxNodeProcess("node", "/sandbox.js");

        process.start();

        assertEquals(PtcSandboxNodeProcess.State.RUNNING, process.state());
    }

    @Test
    void shouldRejectStartWhenAlreadyRunning() {
        PtcSandboxNodeProcess process = new PtcSandboxNodeProcess("node", "/sandbox.js");
        process.start();

        assertThrows(IllegalStateException.class, process::start);
    }

    @Test
    void shouldRejectExecuteBeforeStart() {
        PtcSandboxNodeProcess process = new PtcSandboxNodeProcess("node", "/sandbox.js");

        assertThrows(IllegalStateException.class, () -> process.execute("script"));
    }

    @Test
    void shouldRejectExecuteAfterStop() {
        PtcSandboxNodeProcess process = new PtcSandboxNodeProcess("node", "/sandbox.js");
        process.start();
        process.stop();

        assertThrows(IllegalStateException.class, () -> process.execute("script"));
    }

    @Test
    void shouldExecuteScriptAndReturnResult() {
        PtcSandboxNodeProcess process = new PtcSandboxNodeProcess("node", "/sandbox.js");
        process.start();

        PtcSandboxNodeProcess.PtcResult result = process.execute("console.log('hi')");

        assertNotNull(result);
        assertTrue(result.success());
        assertEquals(0, result.exitCode());
    }

    @Test
    void shouldExecuteScriptWithEnvironment() {
        PtcSandboxNodeProcess process = new PtcSandboxNodeProcess("node", "/sandbox.js");
        process.start();

        PtcSandboxNodeProcess.PtcResult result = process.execute("script", Map.of("KEY", "value"));

        assertEquals("value", result.env().get("KEY"));
    }

    @Test
    void shouldRejectBlankScript() {
        PtcSandboxNodeProcess process = new PtcSandboxNodeProcess("node", "/sandbox.js");
        process.start();

        assertThrows(IllegalArgumentException.class, () -> process.execute("  "));
    }

    @Test
    void shouldTransitionToStoppedOnStop() {
        PtcSandboxNodeProcess process = new PtcSandboxNodeProcess("node", "/sandbox.js");
        process.start();

        process.stop();

        assertEquals(PtcSandboxNodeProcess.State.STOPPED, process.state());
    }

    @Test
    void shouldBeIdempotentOnStop() {
        PtcSandboxNodeProcess process = new PtcSandboxNodeProcess("node", "/sandbox.js");
        process.start();
        process.stop();

        process.stop();

        assertEquals(PtcSandboxNodeProcess.State.STOPPED, process.state());
    }

    @Test
    void shouldHandleNullEnvInExecute() {
        PtcSandboxNodeProcess process = new PtcSandboxNodeProcess("node", "/sandbox.js");
        process.start();

        PtcSandboxNodeProcess.PtcResult result = process.execute("script", null);

        assertTrue(result.env().isEmpty());
    }
}
