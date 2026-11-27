package net.linlan.dsh.domain.tool.subagent;

import net.linlan.dsh.domain.agent.service.subagent.InProcessSubagentDriver;
import net.linlan.dsh.domain.tool.model.error.ToolArgsError;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("子代理控制工具")
class SubagentControlToolsTest {

    @Test
    @DisplayName("SendMessageTool 按名称投递消息")
    void sendMessageByName() {
        var driver = new InProcessSubagentDriver();
        String id = driver.spawn(new InProcessSubagentDriver.SubagentSpec(
                "general", null, "reviewer", "reviews code"));
        var tool = new SendMessageTool(driver);

        var result = tool.execute(Map.of("agent", "reviewer", "message", "hello"));

        assertEquals("reviewer", result.get("agent"));
        assertEquals(id, result.get("subagentId"));
        assertEquals(true, result.get("delivered"));
        assertEquals(1, driver.pendingMessageCount(id));
        assertEquals("hello", driver.pollMessage(id));
    }

    @Test
    @DisplayName("SendMessageTool 兼容会话 id 寻址")
    void sendMessageBySessionId() {
        var driver = new InProcessSubagentDriver();
        String id = driver.spawn(new InProcessSubagentDriver.SubagentSpec("general", null));
        var tool = new SendMessageTool(driver);

        tool.execute(Map.of("agent", id, "message", "hello"));

        assertEquals("hello", driver.pollMessage(id));
    }

    @Test
    @DisplayName("仅传 agent_id 或旧 subagentId 参数校验失败")
    void legacyIdParamsRejected() {
        var tool = new SendMessageTool(new InProcessSubagentDriver());

        var error = assertThrows(ToolArgsError.class,
                () -> tool.execute(Map.of("agent_id", "subagent-1", "message", "x")));
        assertEquals("INVALID_ARGS", error.errorCode());

        assertThrows(ToolArgsError.class,
                () -> tool.execute(Map.of("subagentId", "subagent-1", "message", "x")));
    }

    @Test
    @DisplayName("SendMessageTool 缺少参数抛 ToolArgsError")
    void sendMessageMissingArg() {
        var tool = new SendMessageTool(new InProcessSubagentDriver());
        assertThrows(ToolArgsError.class, () -> tool.execute(Map.of("agent", "x")));
        assertThrows(ToolArgsError.class, () -> tool.execute(Map.of("message", "x")));
    }

    @Test
    @DisplayName("InterruptTool 按名称设置中断标志")
    void interruptByName() {
        var driver = new InProcessSubagentDriver();
        String id = driver.spawn(new InProcessSubagentDriver.SubagentSpec(
                "general", null, "checker", null));
        var tool = new InterruptTool(driver);

        var result = tool.execute(Map.of("agent", "checker"));

        assertEquals("checker", result.get("agent"));
        assertEquals(id, result.get("subagentId"));
        assertEquals(true, result.get("interrupted"));
        assertTrue(driver.isInterrupted(id));
    }

    @Test
    @DisplayName("InterruptTool 缺少参数抛 ToolArgsError")
    void interruptMissingArg() {
        var tool = new InterruptTool(new InProcessSubagentDriver());
        assertThrows(ToolArgsError.class, () -> tool.execute(Map.of()));
    }

    @Test
    @DisplayName("ListAgentsTool 返回名称、状态与描述")
    @SuppressWarnings("unchecked")
    void listAgentsReturnsStructuredRows() {
        var driver = new InProcessSubagentDriver();
        String reviewer = driver.spawn(new InProcessSubagentDriver.SubagentSpec(
                "a", null, "reviewer", "reviews code"));
        String checker = driver.spawn(new InProcessSubagentDriver.SubagentSpec(
                "b", null, "reviewer/checker", null));
        driver.shutdown(checker);
        var tool = new ListAgentsTool(driver);
        tool.register(reviewer);
        tool.register(checker);

        var result = tool.execute(Map.of());
        var agents = (List<Map<String, Object>>) result.get("agents");

        @SuppressWarnings("unchecked")
        var capacity = (Map<String, Object>) result.get("capacity");
        assertEquals(2, capacity.get("total"));
        assertEquals(driver.capacityLimit(), capacity.get("limit"));
        assertEquals("reviewer", agents.get(0).get("name"));
        assertEquals("running", agents.get(0).get("status"));
        assertEquals("reviews code", agents.get(0).get("description"));
        assertEquals("reviewer/checker", agents.get(1).get("name"));
        assertEquals("done", agents.get(1).get("status"));
    }

    @Test
    @DisplayName("ListAgentsTool 空注册返回容量对象 total=0")
    void listAgentsEmpty() {
        var driver = new InProcessSubagentDriver();
        var tool = new ListAgentsTool(driver);
        var result = tool.execute(Map.of());
        @SuppressWarnings("unchecked")
        var capacity = (Map<String, Object>) result.get("capacity");
        assertEquals(0, capacity.get("total"));
        assertEquals(driver.capacityLimit(), capacity.get("limit"));
    }

    @Test
    @DisplayName("默认名称使用会话 id，重名创建被拒绝")
    void defaultNameAndDuplicateRejected() {
        var driver = new InProcessSubagentDriver();
        String id = driver.spawn(new InProcessSubagentDriver.SubagentSpec("a", null));

        assertEquals(id, driver.info(id).name());
        driver.spawn(new InProcessSubagentDriver.SubagentSpec("b", null, "lead", null));
        assertThrows(IllegalArgumentException.class, () -> driver.spawn(
                new InProcessSubagentDriver.SubagentSpec("c", null, "lead", null)));
    }

    @Test
    @DisplayName("null driver 抛异常")
    void nullDriverRejected() {
        assertThrows(NullPointerException.class, () -> new SendMessageTool(null));
        assertThrows(NullPointerException.class, () -> new InterruptTool(null));
        assertThrows(NullPointerException.class, () -> new ListAgentsTool(null));
    }
}
