package net.linlan.dsh.domain.tool.subagent;

import net.linlan.dsh.domain.agent.service.subagent.InProcessSubagentDriver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("子代理控制工具")
class SubagentControlToolsTest {

    @Test
    @DisplayName("SendMessageTool 投递消息")
    void sendMessageDelivers() {
        var driver = new InProcessSubagentDriver();
        var spec = new InProcessSubagentDriver.SubagentSpec("general", "/tmp", null);
        String id = driver.spawn(spec);
        var tool = new SendMessageTool(driver);
        var result = tool.execute(Map.of("subagentId", id, "message", "hello"));
        assertEquals(id, result.get("subagentId"));
        assertEquals(true, result.get("delivered"));
        assertEquals(1, driver.pendingMessageCount(id));
        assertEquals("hello", driver.pollMessage(id));
    }

    @Test
    @DisplayName("SendMessageTool 缺少参数抛异常")
    void sendMessageMissingArg() {
        var tool = new SendMessageTool(new InProcessSubagentDriver());
        assertThrows(IllegalArgumentException.class, () -> tool.execute(Map.of("subagentId", "x")));
        assertThrows(IllegalArgumentException.class, () -> tool.execute(Map.of("message", "x")));
    }

    @Test
    @DisplayName("InterruptTool 设置中断标志")
    void interruptSetsFlag() {
        var driver = new InProcessSubagentDriver();
        var spec = new InProcessSubagentDriver.SubagentSpec("general", "/tmp", null);
        String id = driver.spawn(spec);
        var tool = new InterruptTool(driver);
        var result = tool.execute(Map.of("subagentId", id));
        assertEquals(true, result.get("interrupted"));
        assertTrue(driver.isInterrupted(id));
    }

    @Test
    @DisplayName("InterruptTool 缺少参数抛异常")
    void interruptMissingArg() {
        var tool = new InterruptTool(new InProcessSubagentDriver());
        assertThrows(IllegalArgumentException.class, () -> tool.execute(Map.of()));
    }

    @Test
    @DisplayName("ListAgentsTool 返回已注册且存活的代理")
    void listAgentsReturnsAlive() {
        var driver = new InProcessSubagentDriver();
        var spec1 = new InProcessSubagentDriver.SubagentSpec("a", "/tmp", null);
        var spec2 = new InProcessSubagentDriver.SubagentSpec("b", "/tmp", null);
        String id1 = driver.spawn(spec1);
        String id2 = driver.spawn(spec2);
        var tool = new ListAgentsTool(driver);
        tool.register(id1);
        tool.register(id2);
        var result = tool.execute(Map.of());
        assertEquals(2, result.get("count"));
    }

    @Test
    @DisplayName("ListAgentsTool 排除已 shutdown 的代理")
    void listAgentsExcludesShutdown() {
        var driver = new InProcessSubagentDriver();
        var spec1 = new InProcessSubagentDriver.SubagentSpec("a", "/tmp", null);
        var spec2 = new InProcessSubagentDriver.SubagentSpec("b", "/tmp", null);
        String id1 = driver.spawn(spec1);
        String id2 = driver.spawn(spec2);
        driver.shutdown(id1);
        var tool = new ListAgentsTool(driver);
        tool.register(id1);
        tool.register(id2);
        var result = tool.execute(Map.of());
        assertEquals(1, result.get("count"));
    }

    @Test
    @DisplayName("ListAgentsTool 空注册返回 count=0")
    void listAgentsEmpty() {
        var tool = new ListAgentsTool(new InProcessSubagentDriver());
        var result = tool.execute(Map.of());
        assertEquals(0, result.get("count"));
    }

    @Test
    @DisplayName("null driver 抛异常")
    void nullDriverRejected() {
        assertThrows(NullPointerException.class, () -> new SendMessageTool(null));
        assertThrows(NullPointerException.class, () -> new InterruptTool(null));
        assertThrows(NullPointerException.class, () -> new ListAgentsTool(null));
    }
}
