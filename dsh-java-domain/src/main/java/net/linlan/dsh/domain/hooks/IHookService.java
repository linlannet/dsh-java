package net.linlan.dsh.domain.hooks;

import net.linlan.dsh.domain.hooks.model.valobj.HookDialect;
import net.linlan.dsh.domain.hooks.model.valobj.HookPoint;
import net.linlan.dsh.domain.hooks.model.valobj.MergedHookOutcome;

import java.util.Map;

/**
 * Application-facing hook seam. Matches configured hooks against a hook point
 * and tool name, runs them, and merges outcomes.
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
public interface IHookService {

    /**
     * 处理runHooksHook列表。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param dialect dialect。
     * @param toolName 工具名。
     * @param environment environment。
     * @return 领域处理结果。
     */
    MergedHookOutcome runHooks(HookDialect dialect, HookPoint point, String toolName,
                               String payloadJson, Map<String, String> environment);
}
