package net.linlan.dsh.cases.task.submit.node;

import net.linlan.dsh.api.dto.SubmitTaskRequestDTO;
import net.linlan.dsh.api.dto.SubmitTaskResponseDTO;
import net.linlan.dsh.cases.task.submit.factory.SubmitTaskDynamicContext;
import net.linlan.dsh.cases.orchestration.StrategyHandler;
import net.linlan.dsh.domain.session.IHarnessSessionRegistryService;
import net.linlan.dsh.domain.session.ISessionLogService;
import net.linlan.dsh.domain.task.IHarnessExecutionService;
import net.linlan.dsh.domain.task.IHarnessTaskQueueService;
import net.linlan.dsh.domain.task.execution.model.valobj.RuntimeExecutionPlanVO;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.Instant;

/**
 * 入队节点：创建会话、任务入队、记录日志，并按条件继续执行。
 * 这是任务提交链路的终止节点。
 * <p>
 * 事务边界：「创建会话 + 任务入队 + 记录日志」三步在同一个事务中完成，
 * 保证原子性；executeSession（可能长时间运行的模型调用）在事务外执行，
 * 避免 DB 连接被长时间占用。
 *
 * <p>调用流程：SubmissionRootNode → ProfileResolutionNode → ToolResolutionNode → PermissionCheckNode → EnqueueNode。</p>
 * <p>示例：提交一条任务后，先解析执行画像，再校验权限，最后进入待执行队列。</p>
 *
 */
@Service("submitTaskEnqueueNode")
public class EnqueueNode extends AbstractSubmitTaskNode {

    private static final Logger log = LoggerFactory.getLogger(EnqueueNode.class);

    @Autowired
    private IHarnessSessionRegistryService harnessSessionRegistryService;

    @Autowired
    private IHarnessTaskQueueService harnessTaskQueueService;

    @Autowired
    private ISessionLogService sessionLogService;

    @Autowired
    private IHarnessExecutionService harnessExecutionService;

    @Autowired
    private PlatformTransactionManager transactionManager;

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
        var submission = ctx.getSubmission();
        var profile = ctx.getExecutionProfile();
        var model = ctx.getModelChannel();
        var permission = ctx.getPermissionAssessment();
        var approval = ctx.getApprovalDecision();

        RuntimeExecutionPlanVO runtimePlan = new RuntimeExecutionPlanVO(
                submission.workspaceId(),
                submission.prompt(),
                profile.profileCode(),
                profile.executionMode(),
                model.channelCode(),
                model.modelCode(),
                permission.enabledTools(),
                Instant.now(),
                approval.status()
        );

        // 事务包裹「创建会话 + 任务入队 + 记录日志」三步写操作。
        // executeSession 在事务外执行——它可能触发长时间模型调用，
        // 不能占用 DB 连接。
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        txTemplate.executeWithoutResult(status -> {
            ctx.setSessionResult(harnessSessionRegistryService.openQueuedSession(runtimePlan));
            log.info("[Submit] 创建排队会话完成，sessionId={} resolvedModel={}",
                    ctx.getSessionResult().sessionId(), ctx.getSessionResult().resolvedModel());
            ctx.setTaskResult(harnessTaskQueueService.createQueuedTask(ctx.getSessionResult().sessionId(), runtimePlan));
            ctx.setCurrentStatus(ctx.getTaskResult().status().name());
            log.info("[Submit] 任务入队完成，taskId={} status={}",
                    ctx.getTaskResult().taskId(), ctx.getTaskResult().status().name());

            sessionLogService.recordTaskSubmission(
                    ctx.getSessionResult().sessionId(),
                    ctx.getTaskResult().taskId(),
                    request.profileCode(),
                    request.prompt(),
                    approval.approvalRequired(),
                    approval.reason()
            );
        });

        if (!approval.approvalRequired()) {
            log.info("[Submit] 无需审批，立即触发执行 sessionId={}...", ctx.getSessionResult().sessionId());
            ctx.setCurrentStatus(harnessExecutionService
                    .executeSession(ctx.getSessionResult().sessionId()).status().name());
            log.info("[Submit] 执行触发完成，sessionId={} status={}", ctx.getSessionResult().sessionId(), ctx.getCurrentStatus());
        } else {
            log.info("[Submit] 需要审批，任务挂起等待审批 taskId={}", ctx.getTaskResult().taskId());
        }

        return new SubmitTaskResponseDTO(
                ctx.getTaskResult().taskId(),
                ctx.getSessionResult().sessionId(),
                ctx.getCurrentStatus(),
                ctx.getSessionResult().resolvedChannelCode(),
                ctx.getSessionResult().resolvedModel(),
                ctx.getTaskResult().enabledTools(),
                approval.approvalRequired(),
                approval.reason(),
                ctx.getTaskResult().acceptedAt()
        );
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
        return defaultStrategyHandler;
    }
}
