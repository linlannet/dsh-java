package net.linlan.dsh.domain.coderuntime.adapter.port;

import net.linlan.dsh.domain.coderuntime.model.valobj.CodeRunRequest;
import net.linlan.dsh.domain.coderuntime.model.valobj.CodeRunResult;

/**
 * Port for executing code against host async bindings.
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
public interface ICodeRuntimePort {

    /**
     * 执行领域动作并返回结果。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param request 请求。
     * @return 领域处理结果。
     */
    CodeRunResult execute(CodeRunRequest request);
}
