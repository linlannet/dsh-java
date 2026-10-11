package net.linlan.dsh.domain.task.execution.model.valobj;

import net.linlan.dsh.domain.shared.model.valobj.HarnessStatusEnumVO;
import java.time.Instant;
import java.util.List;

/**
 * RuntimeExecutionPlanVO 表示RuntimeExecutionPlan的领域值对象。
 */
public record RuntimeExecutionPlanVO(
        String workspaceId,
        String prompt,
        String profileCode,
        String executionMode,
        String resolvedChannelCode,
        String resolvedModel,
        List<String> enabledTools,
        Instant acceptedAt,
        HarnessStatusEnumVO status
) {
}
