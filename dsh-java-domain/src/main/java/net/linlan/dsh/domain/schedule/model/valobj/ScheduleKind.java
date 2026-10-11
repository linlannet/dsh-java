package net.linlan.dsh.domain.schedule.model.valobj;

/**
 * ScheduleKind 定义当前模块中的enum契约与职责。
 * <p>调用流程：创建计划 → 到期扫描 → 执行任务 → 更新状态。</p>
 * <p>示例：设置固定间隔后，调度器到期触发任务并记录结果。</p>
 */
public enum ScheduleKind {
    AFTER,
    AT,
    EVERY
}
