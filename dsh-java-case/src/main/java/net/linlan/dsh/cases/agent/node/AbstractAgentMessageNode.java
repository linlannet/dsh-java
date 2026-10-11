package net.linlan.dsh.cases.agent.node;

import net.linlan.dsh.api.dto.AgentMessageRequestDTO;
import net.linlan.dsh.api.dto.AgentMessageResponseDTO;
import net.linlan.dsh.cases.agent.factory.AgentMessageDynamicContext;
import net.linlan.dsh.cases.orchestration.AbstractStrategyRouter;

/**
 * Agent 消息策略树节点的抽象基类。
 *
 * <p>调用流程：HTTP/API 门面 → AgentUseCase → AgentMessageFactory → 策略树 → ReactLoopAgent；会话事件写入 SessionEventLog。</p>
 * <p>示例：调用方传入 agentId、用户消息和可选模型配置，返回助手文本与工具调用结果。</p>
 *
 */
public abstract class AbstractAgentMessageNode
        extends AbstractStrategyRouter<AgentMessageRequestDTO, AgentMessageDynamicContext, AgentMessageResponseDTO> {
}
