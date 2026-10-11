package net.linlan.dsh.domain.coderuntime.service;

import net.linlan.dsh.domain.coderuntime.ICodeRuntimeService;
import net.linlan.dsh.domain.coderuntime.adapter.port.ICodeRuntimePort;
import net.linlan.dsh.domain.coderuntime.model.valobj.CodeRunRequest;
import net.linlan.dsh.domain.coderuntime.model.valobj.CodeRunResult;
import org.springframework.stereotype.Service;

@Service
/**
 * CodeRuntimeService 定义当前模块中的class契约与职责。
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 */
public class CodeRuntimeService implements ICodeRuntimeService {
    private final ICodeRuntimePort port;

    /**
     * 处理编码 运行时 Service。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param port port参数。
     * @return 方法执行结果。
     */
    public CodeRuntimeService(ICodeRuntimePort port) { this.port = port; }

    @Override
    public CodeRunResult execute(CodeRunRequest request) {
        return port.execute(request);
    }
}
