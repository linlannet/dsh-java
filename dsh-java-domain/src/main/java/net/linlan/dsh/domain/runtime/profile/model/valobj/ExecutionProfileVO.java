package net.linlan.dsh.domain.runtime.profile.model.valobj;

/**
 * Describes the resolved execution mode for one profile code.
 *
 * @param profileCode execution profile code
 * @param executionMode resolved execution mode
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
public record ExecutionProfileVO(
        String profileCode,
        String executionMode
) {
}
