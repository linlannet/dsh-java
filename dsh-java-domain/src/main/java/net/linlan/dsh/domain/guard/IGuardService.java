package net.linlan.dsh.domain.guard;

import net.linlan.dsh.domain.guard.model.valobj.GuardDecision;
import java.util.Optional;

/**
 * Application-facing guard seam. Guards observe tool executions and can enrich
 * or block the downstream flow without vetoing the execution itself.
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
public interface IGuardService {

    /**
     * 处理postExecute。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param agentId 智能体ID。
     * @param arguments 参数。
     * @return 领域处理结果。
     */
    GuardDecision postExecute(Optional<String> agentId, String toolName, String arguments, String resultJson);

    /**
     * 处理preStep步骤。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param agentId 智能体ID。
     * @return 领域处理结果。
     */
    void preStep(String agentId, boolean hasUserMessage);
}
