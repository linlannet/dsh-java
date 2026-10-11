package net.linlan.dsh.domain.coderuntime;

import net.linlan.dsh.domain.coderuntime.model.valobj.CodeRunRequest;
import net.linlan.dsh.domain.coderuntime.model.valobj.CodeRunResult;

/**
 * Application-facing code-execution seam.
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
public interface ICodeRuntimeService {
    CodeRunResult execute(CodeRunRequest request);
}
