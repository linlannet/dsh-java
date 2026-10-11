package net.linlan.dsh.domain.workflow.service;

import net.linlan.dsh.domain.workflow.model.valobj.WorkflowResult;

/**
 * 工作流运行事件策略。
 * <p>统一运行结果到外部事件的领域语义：状态、输出和错误信息。</p>
 */
public record WorkflowEventPolicy(
        String runId,
        Kind kind,
        String status,
        String output,
        String error
) {
    /**
     * Kind 表示工作流运行结果的事件类型。
     */
    public enum Kind {
        DONE,
        ERROR
    }

    /**
     * 将已完成或失败的工作流结果转换为事件策略。
     * <p>流程：判断异常 → 映射状态和事件类型 → 携带输出或错误信息。</p>
     * @param runId 运行 ID。
     * @param result 工作流结果。
     * @param error 执行异常，可为 null。
     * @return 工作流事件策略。
     */
    public static WorkflowEventPolicy completed(String runId, WorkflowResult result, Throwable error) {
        if (error != null) {
            return new WorkflowEventPolicy(runId, Kind.ERROR, "ERROR", "", error.getMessage());
        }
        return new WorkflowEventPolicy(
                runId,
                Kind.DONE,
                result.stopReason() == null ? "COMPLETED" : result.stopReason().name(),
                result.value() == null ? "" : result.value().toString(),
                result.error().orElse(null)
        );
    }
}
