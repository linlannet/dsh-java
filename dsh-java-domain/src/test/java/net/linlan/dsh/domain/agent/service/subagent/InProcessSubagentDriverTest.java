package net.linlan.dsh.domain.agent.service.subagent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("InProcessSubagentDriver 进程内子代理驱动")
class InProcessSubagentDriverTest {

    @Test
    @DisplayName("spawn 返回唯一 subagentId")
    void spawnReturnsUniqueId() {
        var driver = new InProcessSubagentDriver();
        var spec = new InProcessSubagentDriver.SubagentSpec("general", "/tmp", "hello");
        String id1 = driver.spawn(spec);
        String id2 = driver.spawn(spec);
        assertNotNull(id1);
        assertNotNull(id2);
        assertNotEquals(id1, id2);
        assertTrue(id1.startsWith("subagent-"));
        assertTrue(id2.startsWith("subagent-"));
    }

    @Test
    @DisplayName("spawn 后 isAlive 为 true")
    void spawnMakesSubagentAlive() {
        var driver = new InProcessSubagentDriver();
        var spec = new InProcessSubagentDriver.SubagentSpec("general", "/tmp", null);
        String id = driver.spawn(spec);
        assertTrue(driver.isAlive(id));
    }

    @Test
    @DisplayName("send 投递消息到 inbox")
    void sendDeliversMessage() {
        var driver = new InProcessSubagentDriver();
        var spec = new InProcessSubagentDriver.SubagentSpec("general", "/tmp", null);
        String id = driver.spawn(spec);
        driver.send(id, "ping");
        assertEquals(1, driver.pendingMessageCount(id));
        assertEquals("ping", driver.pollMessage(id));
    }

    @Test
    @DisplayName("initialMessage 自动进入 inbox")
    void initialMessageEntersInbox() {
        var driver = new InProcessSubagentDriver();
        var spec = new InProcessSubagentDriver.SubagentSpec("general", "/tmp", "init");
        String id = driver.spawn(spec);
        assertEquals(1, driver.pendingMessageCount(id));
        assertEquals("init", driver.pollMessage(id));
    }

    @Test
    @DisplayName("interrupt 标记中断状态")
    void interruptSetsFlag() {
        var driver = new InProcessSubagentDriver();
        var spec = new InProcessSubagentDriver.SubagentSpec("general", "/tmp", null);
        String id = driver.spawn(spec);
        assertFalse(driver.isInterrupted(id));
        driver.interrupt(id);
        assertTrue(driver.isInterrupted(id));
        assertTrue(driver.isAlive(id));
    }

    @Test
    @DisplayName("shutdown 后 isAlive 为 false 且清空 inbox")
    void shutdownKillsAndClearsInbox() {
        var driver = new InProcessSubagentDriver();
        var spec = new InProcessSubagentDriver.SubagentSpec("general", "/tmp", "init");
        String id = driver.spawn(spec);
        driver.send(id, "second");
        driver.shutdown(id);
        assertFalse(driver.isAlive(id));
        assertEquals(0, driver.pendingMessageCount(id));
    }

    @Test
    @DisplayName("shutdown 后 send 抛异常")
    void sendAfterShutdownThrows() {
        var driver = new InProcessSubagentDriver();
        var spec = new InProcessSubagentDriver.SubagentSpec("general", "/tmp", null);
        String id = driver.spawn(spec);
        driver.shutdown(id);
        assertThrows(IllegalStateException.class, () -> driver.send(id, "late"));
    }

    @Test
    @DisplayName("未知 subagentId 操作抛异常")
    void unknownIdThrows() {
        var driver = new InProcessSubagentDriver();
        assertThrows(IllegalArgumentException.class, () -> driver.send("nope", "x"));
        assertThrows(IllegalArgumentException.class, () -> driver.interrupt("nope"));
        assertThrows(IllegalArgumentException.class, () -> driver.shutdown("nope"));
    }

    @Test
    @DisplayName("specOf 返回创建时的 spec")
    void specOfReturnsOriginalSpec() {
        var driver = new InProcessSubagentDriver();
        var spec = new InProcessSubagentDriver.SubagentSpec("coder", "/work", "go");
        String id = driver.spawn(spec);
        assertEquals(spec, driver.specOf(id));
    }

    @Test
    @DisplayName("null spec 抛异常")
    void nullSpecRejected() {
        var driver = new InProcessSubagentDriver();
        assertThrows(NullPointerException.class, () -> driver.spawn(null));
    }

    @Test
    @DisplayName("SubagentSpec null agentPreset 抛异常")
    void specNullAgentPresetRejected() {
        assertThrows(NullPointerException.class, () -> new InProcessSubagentDriver.SubagentSpec(null, "/tmp", "x"));
    }

    @Test
    @DisplayName("SubagentSpec null cwd 抛异常")
    void specNullCwdRejected() {
        assertThrows(NullPointerException.class, () -> new InProcessSubagentDriver.SubagentSpec("general", null, "x"));
    }
}
