package net.linlan.dsh.domain.runtime.profile.service.router;

import org.springframework.stereotype.Service;

/**
 * Resolves the execution mode for headless runs.
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
@Service
public class HeadlessExecutionProfileStrategy implements ExecutionProfileStrategy {

    @Override
    public String profileCode() {
        return "headless";
    }

    @Override
    public String executionMode() {
        return "HEADLESS";
    }
}
