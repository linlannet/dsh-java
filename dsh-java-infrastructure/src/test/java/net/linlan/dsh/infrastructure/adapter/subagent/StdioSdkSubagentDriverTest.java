package net.linlan.dsh.infrastructure.adapter.subagent;

import net.linlan.dsh.domain.agent.service.subagent.adapter.port.ISubagentDriverPort;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StdioSdkSubagentDriverTest {

    @Test
    void implementsSubagentDriverPort() {
        StdioSdkSubagentDriver driver = new StdioSdkSubagentDriver();
        assertTrue(driver instanceof ISubagentDriverPort);
    }

    @Test
    void spawnBlankCommandThrows() {
        StdioSdkSubagentDriver driver = new StdioSdkSubagentDriver();
        assertThrows(IllegalArgumentException.class, () -> driver.spawn("preset", null, "   "));
    }

    @Test
    void spawnNullCommandThrows() {
        StdioSdkSubagentDriver driver = new StdioSdkSubagentDriver();
        assertThrows(IllegalArgumentException.class, () -> driver.spawn("preset", null, null));
    }

    @Test
    void spawnReturnsUniqueId() {
        StdioSdkSubagentDriver driver = new StdioSdkSubagentDriver();
        String id1 = driver.spawn("preset", null, "sleep 30");
        String id2 = driver.spawn("preset", null, "sleep 30");
        assertNotEquals(id1, id2);
        assertTrue(id1.startsWith("sdk-subagent-"));
        assertTrue(id2.startsWith("sdk-subagent-"));
        driver.shutdown(id1);
        driver.shutdown(id2);
    }

    @Test
    void spawnIdIsSequential() {
        StdioSdkSubagentDriver driver = new StdioSdkSubagentDriver();
        String id1 = driver.spawn("preset", null, "sleep 30");
        String id2 = driver.spawn("preset", null, "sleep 30");
        assertTrue(Integer.parseInt(id2.substring("sdk-subagent-".length()))
                > Integer.parseInt(id1.substring("sdk-subagent-".length())));
        driver.shutdown(id1);
        driver.shutdown(id2);
    }

    @Test
    void isAliveReturnsTrueForSpawned() {
        StdioSdkSubagentDriver driver = new StdioSdkSubagentDriver();
        String id = driver.spawn("preset", null, "sleep 30");
        assertTrue(driver.isAlive(id));
        driver.shutdown(id);
    }

    @Test
    void isAliveReturnsFalseAfterShutdown() {
        StdioSdkSubagentDriver driver = new StdioSdkSubagentDriver();
        String id = driver.spawn("preset", null, "sleep 30");
        driver.shutdown(id);
        assertFalse(driver.isAlive(id));
    }

    @Test
    void isAliveReturnsFalseForUnknown() {
        StdioSdkSubagentDriver driver = new StdioSdkSubagentDriver();
        assertFalse(driver.isAlive("unknown"));
    }

    @Test
    void sendToUnknownThrows() {
        StdioSdkSubagentDriver driver = new StdioSdkSubagentDriver();
        assertThrows(IllegalArgumentException.class, () -> driver.send("unknown", "msg"));
    }

    @Test
    void shutdownUnknownIsNoop() {
        StdioSdkSubagentDriver driver = new StdioSdkSubagentDriver();
        driver.shutdown("unknown");
        assertFalse(driver.isAlive("unknown"));
    }

    @Test
    void spawnWithCwd() {
        StdioSdkSubagentDriver driver = new StdioSdkSubagentDriver();
        String id = driver.spawn("preset", System.getProperty("java.io.tmpdir"), "sleep 30");
        assertTrue(driver.isAlive(id));
        driver.shutdown(id);
    }

    @Test
    void spawnWithBlankCwdUsesNoDirectory() {
        StdioSdkSubagentDriver driver = new StdioSdkSubagentDriver();
        String id = driver.spawn("preset", "  ", "sleep 30");
        assertTrue(driver.isAlive(id));
        driver.shutdown(id);
    }

    @Test
    void shutdownIsIdempotent() {
        StdioSdkSubagentDriver driver = new StdioSdkSubagentDriver();
        String id = driver.spawn("preset", null, "sleep 30");
        driver.shutdown(id);
        driver.shutdown(id);
        assertFalse(driver.isAlive(id));
    }

    @Test
    void spawnParsesCommandWithArgs() {
        StdioSdkSubagentDriver driver = new StdioSdkSubagentDriver();
        String id = driver.spawn("preset", null, "sleep 30");
        assertTrue(driver.isAlive(id));
        driver.shutdown(id);
    }

    @Test
    void sendParsesJsonRpcResult() throws Exception {
        java.nio.file.Path script = java.nio.file.Files.createTempFile("subagent", ".sh");
        java.nio.file.Files.writeString(script,
                "read line\necho '{\"jsonrpc\":\"2.0\",\"result\":\"pong\"}'\n");
        script.toFile().setExecutable(true);
        StdioSdkSubagentDriver driver = new StdioSdkSubagentDriver();
        String id = driver.spawn("preset", null, "sh " + script);
        try {
            String response = driver.send(id, "hello");
            assertEquals("pong", response);
        } finally {
            driver.shutdown(id);
            java.nio.file.Files.deleteIfExists(script);
        }
    }

    @Test
    void sendReturnsRawResponseWhenNoResultField() throws Exception {
        java.nio.file.Path script = java.nio.file.Files.createTempFile("subagent", ".sh");
        java.nio.file.Files.writeString(script,
                "read line\necho '{\"jsonrpc\":\"2.0\",\"id\":1}'\n");
        script.toFile().setExecutable(true);
        StdioSdkSubagentDriver driver = new StdioSdkSubagentDriver();
        String id = driver.spawn("preset", null, "sh " + script);
        try {
            String response = driver.send(id, "hello");
            assertNotNull(response);
            assertTrue(response.contains("jsonrpc"));
        } finally {
            driver.shutdown(id);
            java.nio.file.Files.deleteIfExists(script);
        }
    }

    @Test
    void sendThrowsWhenSubagentStdoutClosed() throws Exception {
        StdioSdkSubagentDriver driver = new StdioSdkSubagentDriver();
        String id = driver.spawn("preset", null, "true");
        Thread.sleep(200);
        try {
            assertThrows(java.io.UncheckedIOException.class, () -> driver.send(id, "msg"));
        } finally {
            driver.shutdown(id);
        }
    }

    @Test
    void sendToUnknownIdAfterShutdownThrows() {
        StdioSdkSubagentDriver driver = new StdioSdkSubagentDriver();
        String id = driver.spawn("preset", null, "sleep 30");
        driver.shutdown(id);
        assertThrows(IllegalArgumentException.class, () -> driver.send(id, "msg"));
    }
}
