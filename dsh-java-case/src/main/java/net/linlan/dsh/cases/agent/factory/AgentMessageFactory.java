package net.linlan.dsh.cases.agent.factory;

import net.linlan.dsh.api.dto.AgentMessageRequestDTO;
import net.linlan.dsh.api.dto.AgentMessageResponseDTO;
import net.linlan.dsh.api.dto.AgentToolCallEventResponseDTO;
import net.linlan.dsh.cases.agent.node.AgentResolveNode;
import net.linlan.dsh.cases.orchestration.CasePipeline;
import net.linlan.dsh.cases.orchestration.CasePipelineFactory;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import java.util.function.Consumer;

/**
 * Agent 消息策略树工厂。
 * <p>核心流程：UseCase 先取根策略节点，再按 Resolve -> Intent -> Dispatch -> Collect 处理消息。
 * <p>数据示例：用户发送“帮我查看 /tmp/a.txt”，Resolve 复用或创建 Agent，Intent 识别为任务/文件意图，
 * Dispatch 交给 ReactLoopAgent 调用文件工具，Collect 汇总最终回复。
 *
 *
 */
@Service
public class AgentMessageFactory
        implements CasePipelineFactory<AgentMessageRequestDTO, AgentMessageDynamicContext, AgentMessageResponseDTO> {

    @Autowired
    @Qualifier("agentMessageResolveNode")
    private AgentResolveNode rootNode;

    /**
     * 返回 Agent 消息策略树的根节点。
     * <p>根节点固定为 {@link AgentResolveNode}，后续节点由每个节点自己的 getNext 规则连接。
     *
     * @return 可直接 apply 的消息处理策略
     */
    public CasePipeline<AgentMessageRequestDTO, AgentMessageDynamicContext, AgentMessageResponseDTO> pipeline() {
        return CasePipeline.of("Agent message", rootNode, AgentMessageDynamicContext::new);
    }

    /**
     * 构建当前用例的执行 Pipeline，绑定首节点和上下文。
     */
    public CasePipeline<AgentMessageRequestDTO, AgentMessageDynamicContext, AgentMessageResponseDTO> streamingPipeline(
            Consumer<String> deltaSink,
            Consumer<String> reasoningSink,
            Consumer<AgentToolCallEventResponseDTO> toolCallEventSink,
            Runnable finishSink
    ) {
        return CasePipeline.of(
                "Agent message streaming",
                rootNode,
                () -> {
                    AgentMessageDynamicContext context = new AgentMessageDynamicContext();
                    context.setDeltaSink(deltaSink);
                    context.setReasoningSink(reasoningSink);
                    context.setToolCallEventSink(toolCallEventSink);
                    context.setFinishSink(finishSink);
                    return context;
                }
        );
    }
}
