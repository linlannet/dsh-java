package net.linlan.dsh.cases.agent;

import net.linlan.dsh.api.dto.AgentMessageRequestDTO;
import net.linlan.dsh.api.dto.AgentMessageResponseDTO;
import net.linlan.dsh.api.dto.AgentToolCallEventResponseDTO;
import net.linlan.dsh.cases.agent.factory.AgentMessageFactory;
import net.linlan.dsh.domain.agent.model.valobj.AgentCancelCause;
import net.linlan.dsh.domain.agent.service.run.AgentRun;
import net.linlan.dsh.domain.agent.contract.inbound.AgentRunLifecycle;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Agent 交互用例。
 * 消息发送通过工厂委托给策略树处理。
 *
 * <p>调用流程：HTTP/API 门面 → AgentUseCase → AgentMessageFactory → 策略树 → ReactLoopAgent；会话事件写入 SessionEventLog。</p>
 * <p>示例：调用方传入 agentId、用户消息和可选模型配置，返回助手文本与工具调用结果。</p>
 *
 */
@Service
public class AgentUseCase implements IAgentUseCase {

    private static final Logger log = LoggerFactory.getLogger(AgentUseCase.class);

    @Autowired
    private AgentMessageFactory agentMessageFactory;

    @Autowired
    private AgentRunLifecycle agentFactory;

    /**
     * 发送Message。
     * 流程：读取命令 → 执行用例编排 → 返回结果。
     * @param request 请求对象。
     * @return 方法执行结果。
     */

    @Override
    public AgentMessageResponseDTO sendMessage(AgentMessageRequestDTO request) {
        log.info("[UseCase] Agent={} 收到消息发送请求（channel={} streaming=false）",
                request.agentId(), request.channelCode());
        return agentMessageFactory.pipeline().execute(request);
    }

    /**
     * 发送Message Streaming。
     * 流程：读取命令 → 执行用例编排 → 返回结果。
     * @param request 请求对象。
     * @param deltaSink 文本增量接收器。
     * @param toolCallSink 工具调用事件接收器。
     * @return 方法执行结果。
     */

    @Override
    public AgentMessageResponseDTO sendMessageStreaming(
            AgentMessageRequestDTO request,
            java.util.function.Consumer<String> deltaSink,
            java.util.function.Consumer<String> reasoningSink,
            java.util.function.Consumer<AgentToolCallEventResponseDTO> toolCallSink,
            Runnable finishSink) {
        log.info("[UseCase] Agent={} 收到流式消息发送请求（channel={}）",
                request.agentId(), request.channelCode());
        return agentMessageFactory.streamingPipeline(deltaSink, reasoningSink, toolCallSink, finishSink).execute(request);
    }

    /**
     * 获取Agent Status。
     * 流程：读取命令 → 执行用例编排 → 返回结果。
     * @param agentId Agent ID。
     * @return 获取到的Agent Status。
     */

    @Override
    public String getAgentStatus(String agentId) {
        AgentRun agent = agentFactory.get(agentId);
        if (agent == null) return "not-found";
        return agent.status().name().toLowerCase();
    }

    /**
     * 取消Agent。
     * 流程：读取命令 → 执行用例编排 → 返回结果。
     * @param agentId Agent ID。
     */

    @Override
    public void cancelAgent(String agentId) {
        AgentRun agent = agentFactory.get(agentId);
        if (agent != null) {
            agent.cancel(new AgentCancelCause.UserCancel("user requested"), false);
        }
    }

}
