package net.linlan.dsh.cases.task.submit.node;

import net.linlan.dsh.api.dto.SubmitTaskRequestDTO;
import net.linlan.dsh.api.dto.SubmitTaskResponseDTO;
import net.linlan.dsh.cases.task.submit.factory.SubmitTaskDynamicContext;
import net.linlan.dsh.cases.orchestration.StrategyHandler;
import net.linlan.dsh.domain.task.IApprovalPolicyService;
import net.linlan.dsh.domain.task.IPermissionPolicyService;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 权限检查节点：评估权限并决定是否需要审批。
 *
 * <p>调用流程：SubmissionRootNode → ProfileResolutionNode → ToolResolutionNode → PermissionCheckNode → EnqueueNode。</p>
 * <p>示例：提交一条任务后，先解析执行画像，再校验权限，最后进入待执行队列。</p>
 *
 */
@Service("submitTaskPermissionCheckNode")
public class PermissionCheckNode extends AbstractSubmitTaskNode {

    private static final Logger log = LoggerFactory.getLogger(PermissionCheckNode.class);

    @Autowired
    private IPermissionPolicyService permissionPolicyService;

    @Autowired
    private IApprovalPolicyService approvalPolicyService;

    @Autowired
    @Qualifier("submitTaskEnqueueNode")
    private EnqueueNode enqueueNode;

    /**
     * do Apply。
     * 流程：读取命令 → 执行用例编排 → 返回结果。
     * @param request 请求对象。
     * @param ctx ctx参数。
     * @return 方法执行结果。
     */

    @Override
    protected SubmitTaskResponseDTO doApply(SubmitTaskRequestDTO request,
                                         SubmitTaskDynamicContext ctx) throws Exception {
        ctx.setPermissionAssessment(permissionPolicyService.assess(ctx.getEnabledTools()));
        log.info("[Submit] 权限评估完成，启用工具={}", ctx.getPermissionAssessment().enabledTools());
        ctx.setApprovalDecision(approvalPolicyService.decide(
                ctx.getSubmission().profileCode(),
                ctx.getPermissionAssessment()
        ));
        log.info("[Submit] 审批决策完成，需审批={} 原因={}",
                ctx.getApprovalDecision().approvalRequired(), ctx.getApprovalDecision().reason());
        return null;
    }

    @Override
    public StrategyHandler<SubmitTaskRequestDTO, SubmitTaskDynamicContext, SubmitTaskResponseDTO>
    /**
     * 获取Next。
     * 流程：读取命令 → 执行用例编排 → 返回结果。
     * @param request 请求对象。
     * @param ctx ctx参数。
     * @return 获取到的Next。
     */

    getNext(SubmitTaskRequestDTO request, SubmitTaskDynamicContext ctx) {
        return enqueueNode;
    }
}
