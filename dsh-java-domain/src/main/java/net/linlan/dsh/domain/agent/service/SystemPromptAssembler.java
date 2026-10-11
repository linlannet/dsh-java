package net.linlan.dsh.domain.agent.service;

import net.linlan.dsh.domain.agent.model.entity.AgentOptions;
import net.linlan.dsh.domain.model.valobj.ToolSchema;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * SystemPromptAssembler 表示系统提示词的领域装配契约。
 */
public interface SystemPromptAssembler {

    /**
     * 装配领域所需的内容结构。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param agentId 智能体ID。
     * @param cwd 工作目录。
     * @return 领域处理结果。
     */
    CompletableFuture<PromptAssembly> assemble(
            String agentId,
            AgentOptions options,
            String cwd,
            List<ToolSchema> tools
    );

    /**
     * PromptAssembly 表示提示词PromptAssembly。
     */
    record PromptAssembly(
            String system,
            List<ToolSchema> tools
    ) {}
}
