package net.linlan.dsh.domain.task.execution.model.valobj;

/**
 * ModelExecutionResultVO 表示模型ModelExecutionResult结果的领域值对象。
 */
public record ModelExecutionResultVO(
        String providerCode,
        String modelCode,
        String content
) {
}
