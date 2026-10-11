package net.linlan.dsh.cases.task.submit.node;

import net.linlan.dsh.api.dto.SubmitTaskRequestDTO;
import net.linlan.dsh.api.dto.SubmitTaskResponseDTO;
import net.linlan.dsh.cases.task.submit.factory.SubmitTaskDynamicContext;
import net.linlan.dsh.cases.orchestration.StrategyHandler;
import net.linlan.dsh.domain.runtime.IToolResolutionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

/**
 * 工具解析节点：解析启用的工具列表。
 *
 * <p>调用流程：SubmissionRootNode → ProfileResolutionNode → ToolResolutionNode → PermissionCheckNode → EnqueueNode。</p>
 * <p>示例：提交一条任务后，先解析执行画像，再校验权限，最后进入待执行队列。</p>
 *
 */
@Service("submitTaskToolResolutionNode")
public class ToolResolutionNode extends AbstractSubmitTaskNode {

    private static final Logger log = LoggerFactory.getLogger(ToolResolutionNode.class);

    @Autowired
    private IToolResolutionService toolResolutionService;

    @Autowired
    @Qualifier("submitTaskPermissionCheckNode")
    private PermissionCheckNode permissionCheckNode;

    /**
     * 执行当前节点核心逻辑：校验输入、调用领域服务并返回输出。
     */
    @Override
    protected SubmitTaskResponseDTO doApply(SubmitTaskRequestDTO request,
                                         SubmitTaskDynamicContext ctx) throws Exception {
        var submission = ctx.getSubmission();
        var enabledTools = toolResolutionService.resolveEnabledTools(submission.toolSelection().toolCodes());
        ctx.setEnabledTools(enabledTools);
        log.debug("[Submit] 工具解析完成，enabledTools={}", enabledTools);
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
        return permissionCheckNode;
    }
}
