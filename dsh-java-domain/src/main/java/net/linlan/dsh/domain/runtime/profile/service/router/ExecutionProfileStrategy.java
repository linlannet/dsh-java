package net.linlan.dsh.domain.runtime.profile.service.router;

/**
 * Resolves the execution mode for one profile code.
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
public interface ExecutionProfileStrategy {

    /**
     * 处理画像profileCode。
     * @return 领域处理结果。
     */
    String profileCode();

    /**
     * 处理executionMode。
     * @return 领域处理结果。
     */
    String executionMode();
}
