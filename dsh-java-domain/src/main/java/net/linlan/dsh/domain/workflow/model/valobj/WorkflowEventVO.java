package net.linlan.dsh.domain.workflow.model.valobj;

import net.linlan.dsh.domain.workflow.service.WorkflowEventPolicy;

/**
 * WorkflowEventVO 表示工作流对外事件的领域值对象。
 * <p>承载运行 ID、事件类型、状态、输出和错误信息。</p>
 */
public record WorkflowEventVO(
        String runId,
        WorkflowEventKindVO kind,
        String status,
        String output,
        String error
) {
    /**
     * 根据工作流事件策略构建对外事件。
     * <p>流程：读取策略结果 → 映射事件类型 → 组装事件值对象。</p>
     * @param policy 工作流事件策略。
     * @return 工作流事件值对象。
     */
    public static WorkflowEventVO fromPolicy(WorkflowEventPolicy policy) {
        return new WorkflowEventVO(
                policy.runId(),
                policy.kind() == WorkflowEventPolicy.Kind.ERROR
                        ? WorkflowEventKindVO.ERROR
                        : WorkflowEventKindVO.DONE,
                policy.status(),
                policy.output(),
                policy.error()
        );
    }
}
