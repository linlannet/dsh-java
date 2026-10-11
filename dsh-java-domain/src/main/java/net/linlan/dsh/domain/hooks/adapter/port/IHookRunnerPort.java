package net.linlan.dsh.domain.hooks.adapter.port;

import net.linlan.dsh.domain.hooks.model.valobj.CommandHook;
import net.linlan.dsh.domain.hooks.model.valobj.HookOutput;
import java.util.Map;

/**
 * Port for running a single hook command and parsing its output.
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
public interface IHookRunnerPort {

    /**
     * 处理run。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param hook hook。
     * @param environment environment。
     * @return 领域处理结果。
     */
    HookOutput run(CommandHook hook, String payloadJson, Map<String, String> environment);
}
