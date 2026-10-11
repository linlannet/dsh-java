package net.linlan.dsh.cases.agent.factory;

import net.linlan.dsh.api.dto.AgentToolCallEventResponseDTO;
import net.linlan.dsh.domain.agent.service.run.AgentRun;
import net.linlan.dsh.domain.tool.service.ToolCallExecutor;
import net.linlan.dsh.domain.model.entity.Message;

import java.util.function.Consumer;

/**
 * Agent 消息策略树中流转的可变上下文。
 *
 * <p>调用流程：HTTP/API 门面 → AgentUseCase → AgentMessageFactory → 策略树 → ReactLoopAgent；会话事件写入 SessionEventLog。</p>
 * <p>示例：调用方传入 agentId、用户消息和可选模型配置，返回助手文本与工具调用结果。</p>
 *
 */
public class AgentMessageDynamicContext implements AutoCloseable {

    /**
     * MessageIntent 定义当前模块中的enum契约与职责。
     */
    public enum MessageIntent {
        CHAT,
        CODE_QUESTION,
        TASK_EXECUTION,
        CLARIFICATION
    }

    private AgentRun agent;
    private Message userMessage;
    private MessageIntent intent;
    private Consumer<String> deltaSink;
    private Consumer<String> reasoningSink;
    private Consumer<AgentToolCallEventResponseDTO> toolCallEventSink;
    private Runnable finishSink;

    /**
     * 获取Agent。
     */
    public AgentRun getAgent() { return agent; }
    /**
     * 设置Agent。
     */
    public void setAgent(AgentRun agent) { this.agent = agent; }

    /**
     * 获取User Message。
     */
    public Message getUserMessage() { return userMessage; }
    /**
     * 设置User Message。
     */
    public void setUserMessage(Message userMessage) { this.userMessage = userMessage; }

    /**
     * 获取Intent。
     */
    public MessageIntent getIntent() { return intent; }
    /**
     * 设置Intent。
     */
    public void setIntent(MessageIntent intent) { this.intent = intent; }

    /**
     * 读取流式增量输出接收器。
     */
    public Consumer<String> getDeltaSink() { return deltaSink; }

    /**
     * 写入流式增量输出接收器，供 Agent 循环回调。
     */
    public void setDeltaSink(Consumer<String> deltaSink) { this.deltaSink = deltaSink; }

    public void setReasoningSink(Consumer<String> reasoningSink) { this.reasoningSink = reasoningSink; }

    /**
     * 读取工具调用事件接收器。
     */
    public Consumer<AgentToolCallEventResponseDTO> getToolCallEventSink() { return toolCallEventSink; }

    /**
     * 写入工具调用事件接收器，供 Agent 循环回调。
     */
    public void setToolCallEventSink(Consumer<AgentToolCallEventResponseDTO> toolCallEventSink) {
        this.toolCallEventSink = toolCallEventSink;
    }

    public void setFinishSink(Runnable finishSink) { this.finishSink = finishSink; }

    /**
     * 将上下文中的流式接收器绑定到 Agent 执行循环。
     */
    public void attachSinks(AgentRun agent) {
        if (deltaSink == null && toolCallEventSink == null) {
            return;
        }
        agent.setStreamDeltaSink(deltaSink);
        agent.setStreamReasoningSink(reasoningSink);
        agent.setStreamToolCallSink(toolCall -> {
            if (toolCallEventSink != null) {
                toolCallEventSink.accept(new AgentToolCallEventResponseDTO(
                        toolCall.id(), toolCall.name(), toolCall.arguments(), "running"
                ));
            }
        });
        agent.setStreamToolEventSink(event -> {
            if (toolCallEventSink != null) {
                toolCallEventSink.accept(new AgentToolCallEventResponseDTO(
                        event.callId(),
                        event.toolName(),
                        event.args(),
                        event.status(),
                        event.phase(),
                        event.result(),
                        event.durationMs()
                ));
            }
        });
        agent.setStreamFinishSink(finishSink);
    }

    @Override
    /**
     * 解绑流式接收器并清理上下文引用。
     */
    public void close() {
        if (agent != null) {
            agent.setStreamDeltaSink(null);
            agent.setStreamReasoningSink(null);
        agent.setStreamToolCallSink(null);
        agent.setStreamFinishSink(null);
        }
        deltaSink = null;
        reasoningSink = null;
        toolCallEventSink = null;
    }
}
