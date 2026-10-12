package net.linlan.dsh.domain.agent.service.subagent;

import net.linlan.dsh.domain.agent.service.subagent.adapter.port.ISubagentDriverPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SdkSubagentDriver 进程外 SDK 子代理驱动")
class SdkSubagentDriverTest {

    static final class StubPort implements ISubagentDriverPort {
        final List<String> spawned = new ArrayList<>();
        final List<String> sent = new ArrayList<>();
        final List<String> shutdown = new ArrayList<>();
        int nextId = 0;

        @Override
        public String spawn(String agentPreset, String cwd, String command) {
            String id = "stub-" + (++nextId);
            spawned.add(id + "|" + agentPreset + "|" + command);
            return id;
        }

        @Override
        public String send(String subagentId, String message) {
            sent.add(subagentId + "|" + message);
            return "ack:" + message;
        }

        @Override
        public void shutdown(String subagentId) {
            shutdown.add(subagentId);
        }

        @Override
        public boolean isAlive(String subagentId) {
            return !shutdown.contains(subagentId);
        }
    }

    @Test
    @DisplayName("spawn 返回 subagentId 并通过端口启动")
    void spawnDelegatesToPort() {
        var port = new StubPort();
        var driver = new SdkSubagentDriver(port);
        var spec = new SdkSubagentDriver.SubagentSpec("general", "/tmp", "node sdk.js", null);
        String id = driver.spawn(spec);
        assertNotNull(id);
        assertEquals(1, port.spawned.size());
        assertTrue(port.spawned.get(0).contains("general"));
        assertTrue(port.spawned.get(0).contains("node sdk.js"));
    }

    @Test
    @DisplayName("initialMessage 非空时 spawn 后自动发送")
    void spawnWithInitialMessageSends() {
        var port = new StubPort();
        var driver = new SdkSubagentDriver(port);
        var spec = new SdkSubagentDriver.SubagentSpec("general", "/tmp", "node sdk.js", "hello");
        driver.spawn(spec);
        assertEquals(1, port.sent.size());
        assertTrue(port.sent.get(0).endsWith("|hello"));
    }

    @Test
    @DisplayName("initialMessage 为空时不发送")
    void spawnWithoutInitialMessageDoesNotSend() {
        var port = new StubPort();
        var driver = new SdkSubagentDriver(port);
        var spec = new SdkSubagentDriver.SubagentSpec("general", "/tmp", "node sdk.js", null);
        driver.spawn(spec);
        assertEquals(0, port.sent.size());
    }

    @Test
    @DisplayName("send 返回端口响应")
    void sendReturnsResponse() {
        var port = new StubPort();
        var driver = new SdkSubagentDriver(port);
        var spec = new SdkSubagentDriver.SubagentSpec("general", "/tmp", "node sdk.js", null);
        String id = driver.spawn(spec);
        String resp = driver.send(id, "ping");
        assertEquals("ack:ping", resp);
    }

    @Test
    @DisplayName("shutdown 委托端口")
    void shutdownDelegates() {
        var port = new StubPort();
        var driver = new SdkSubagentDriver(port);
        var spec = new SdkSubagentDriver.SubagentSpec("general", "/tmp", "node sdk.js", null);
        String id = driver.spawn(spec);
        assertTrue(driver.isAlive(id));
        driver.shutdown(id);
        assertFalse(driver.isAlive(id));
        assertEquals(List.of(id), port.shutdown);
    }

    @Test
    @DisplayName("null port 抛异常")
    void nullPortRejected() {
        assertThrows(NullPointerException.class, () -> new SdkSubagentDriver(null));
    }

    @Test
    @DisplayName("null spec 抛异常")
    void nullSpecRejected() {
        var driver = new SdkSubagentDriver(new StubPort());
        assertThrows(NullPointerException.class, () -> driver.spawn(null));
    }

    @Test
    @DisplayName("SubagentSpec null 字段校验")
    void specNullFieldsRejected() {
        assertThrows(NullPointerException.class, () -> new SdkSubagentDriver.SubagentSpec(null, "/tmp", "cmd", null));
        assertThrows(NullPointerException.class, () -> new SdkSubagentDriver.SubagentSpec("general", null, "cmd", null));
        assertThrows(NullPointerException.class, () -> new SdkSubagentDriver.SubagentSpec("general", "/tmp", null, null));
    }

    @Test
    @DisplayName("send null 参数校验")
    void sendNullArgsRejected() {
        var driver = new SdkSubagentDriver(new StubPort());
        var spec = new SdkSubagentDriver.SubagentSpec("general", "/tmp", "node sdk.js", null);
        String id = driver.spawn(spec);
        assertThrows(NullPointerException.class, () -> driver.send(null, "msg"));
        assertThrows(NullPointerException.class, () -> driver.send(id, null));
    }
}
