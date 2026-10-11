package net.linlan.dsh.domain.workflow.service;

import net.linlan.dsh.domain.workflow.model.entity.WorkflowStartRequest;
import net.linlan.dsh.domain.workflow.model.valobj.WorkflowMeta;
import java.util.Map;

/**
 * 工作流启动策略。
 * <p>把临时脚本的命名、参数装配等启动规则收敛到领域层，供编排层统一调用。</p>
 */
public final class WorkflowStartPolicy {

    public static final String ADHOC_WORKFLOW_NAME = "adhoc-workflow";

    private WorkflowStartPolicy() {
    }

    public static WorkflowStartRequest adhoc(
            String script,
            String requestedName,
            Map<String, Object> args,
            String parentCwd
    ) {
        String name = requestedName == null || requestedName.isBlank()
                ? ADHOC_WORKFLOW_NAME
                : requestedName;
        return new WorkflowStartRequest(
                script,
                new WorkflowMeta(name, null, java.util.Optional.empty(), java.util.List.of()),
                java.util.Optional.of(args),
                java.util.Optional.empty(),
                java.util.Optional.empty(),
                null,
                parentCwd
        );
    }
}
