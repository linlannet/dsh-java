package net.linlan.dsh.domain.shared.model.valobj;

/**
 * Represents the shared lifecycle state used by task and session domains.
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
public enum HarnessStatusEnumVO {
    CREATED,
    PENDING_APPROVAL,
    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED
}
