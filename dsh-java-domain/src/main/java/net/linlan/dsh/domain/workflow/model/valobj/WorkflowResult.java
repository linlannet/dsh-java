package net.linlan.dsh.domain.workflow.model.valobj;

import java.util.Optional;

/**
 * 工作流运行结果。
 * <p>
 * 执行流程：
 * 1. 工作流运行结束后产出结果；
 * 2. 调用方根据停止原因判断后续处理；
 * 3. 作为运行句柄的最终回传值。
 *
 * @param value 脚本返回值
 * @param stopReason 停止原因
 * @param error 失败信息
 * @param agentCount 接受的 agent() 调用次数
 *
 *
 */
public record WorkflowResult(Object value, WorkflowStopReason stopReason, Optional<String> error, int agentCount) {
    /**
     * 创建已完成结果。
     * <p>
     * 执行流程：
     * 1. 传入脚本返回值；
     * 2. 标记为完成；
     * 3. 返回结果对象。
     */
    public static WorkflowResult completed(Object value, int agentCount) {
        return new WorkflowResult(value, WorkflowStopReason.COMPLETED, Optional.empty(), agentCount);
    }
    /**
     * 创建已取消结果。
     * <p>
     * 执行流程：
     * 1. 不携带业务返回值；
     * 2. 标记为取消；
     * 3. 返回结果对象。
     */
    public static WorkflowResult cancelled(int agentCount) {
        return new WorkflowResult(null, WorkflowStopReason.CANCELLED, Optional.of("cancelled"), agentCount);
    }
    /**
     * 创建异常结果。
     * <p>
     * 执行流程：
     * 1. 传入错误信息；
     * 2. 标记为异常结束；
     * 3. 返回结果对象。
     */
    public static WorkflowResult error(String error, int agentCount) {
        return new WorkflowResult(null, WorkflowStopReason.ERROR, Optional.of(error), agentCount);
    }
}
