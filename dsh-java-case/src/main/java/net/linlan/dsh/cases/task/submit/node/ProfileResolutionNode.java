package net.linlan.dsh.cases.task.submit.node;

import net.linlan.dsh.api.dto.SubmitTaskRequestDTO;
import net.linlan.dsh.api.dto.SubmitTaskResponseDTO;
import net.linlan.dsh.cases.task.submit.factory.SubmitTaskDynamicContext;
import net.linlan.dsh.cases.orchestration.StrategyHandler;
import net.linlan.dsh.domain.runtime.IExecutionProfileService;
import net.linlan.dsh.domain.runtime.IModelRoutingService;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

/**
 * 档位解析节点：解析执行档位和模型渠道路由。
 *
 * <p>调用流程：SubmissionRootNode → ProfileResolutionNode → ToolResolutionNode → PermissionCheckNode → EnqueueNode。</p>
 * <p>示例：提交一条任务后，先解析执行画像，再校验权限，最后进入待执行队列。</p>
 *
 */
@Service("submitTaskProfileResolutionNode")
public class ProfileResolutionNode extends AbstractSubmitTaskNode {

    @Autowired
    private IExecutionProfileService executionProfileService;

    @Autowired
    private IModelRoutingService modelRoutingService;

    @Autowired
    @Qualifier("submitTaskToolResolutionNode")
    private ToolResolutionNode toolResolutionNode;

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
        ctx.setExecutionProfile(executionProfileService.resolve(submission.profileCode()));
        ctx.setModelChannel(modelRoutingService.resolve(submission.channelCode()));
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
        return toolResolutionNode;
    }
}
