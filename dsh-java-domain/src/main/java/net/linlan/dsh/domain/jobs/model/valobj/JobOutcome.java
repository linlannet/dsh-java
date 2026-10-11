package net.linlan.dsh.domain.jobs.model.valobj;

/**
 * JobOutcome 定义当前模块中的record契约与职责。
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 */
public record JobOutcome(JobStatus status, String detail, String output) {}
