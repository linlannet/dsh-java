package net.linlan.dsh.domain.agent.service.subagent;

import net.linlan.dsh.domain.model.entity.MessageSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertFalse;

@DisplayName("子 agent 运行期契约：agent-message 来源与父会话传递")
class SubagentDelegationContractTest {

    @Test
    @DisplayName("委派来源使用父会话 id 标记 agent-message")
    void delegationSourceUsesParentSessionId() {
        MessageSource source = SubagentSessions.delegationSource("parent-1");
        MessageSource.AgentMessageSource agentMessage =
                assertInstanceOf(MessageSource.AgentMessageSource.class, source);
        assertEquals("parent-1", agentMessage.from());
    }

    @Test
    @DisplayName("父会话 id 缺失时回退为 parent")
    void delegationSourceFallsBackToParent() {
        MessageSource source = SubagentSessions.delegationSource(null);
        MessageSource.AgentMessageSource agentMessage =
                assertInstanceOf(MessageSource.AgentMessageSource.class, source);
        assertEquals("parent", agentMessage.from());
    }

    @Test
    @DisplayName("agent-message 来源不是普通用户来源")
    void agentMessageSourceIsNotUserSource() {
        MessageSource source = SubagentSessions.delegationSource("parent-2");
        assertFalse(source instanceof MessageSource.UserSource);
    }

    @Test
    @DisplayName("SubagentTool 将父会话 id 传入启动请求")
    void subagentToolCarriesParentSessionId() {
        SubagentRegistry registry = new SubagentRegistry();
        List<SubagentProvider.SubagentStartRequest> captured = new ArrayList<>();
        registry.register(new SubagentProvider() {
            @Override
            public String name() {
                return "spawn-in-process";
            }

            @Override
            public SubagentCapabilities capabilities() {
                return new SubagentCapabilities(true, true, true);
            }

            @Override
            public CompletableFuture<SubagentResult> start(SubagentStartRequest request) {
                captured.add(request);
                return CompletableFuture.completedFuture(
                        new SubagentResult("completed", List.of(), "run-1"));
            }
        });

        var tool = new SubagentTool(registry, "spawn-in-process", "subagent", null, "parent-9");
        tool.execute(Map.of("prompt", "do it"), null).join();

        assertEquals(1, captured.size());
        assertEquals("parent-9", captured.get(0).parentSessionId());
        assertEquals("do it", captured.get(0).prompt());
    }
}
