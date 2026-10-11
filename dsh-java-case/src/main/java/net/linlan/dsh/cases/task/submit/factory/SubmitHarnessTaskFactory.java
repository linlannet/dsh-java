package net.linlan.dsh.cases.task.submit.factory;

import net.linlan.dsh.api.dto.SubmitTaskRequestDTO;
import net.linlan.dsh.api.dto.SubmitTaskResponseDTO;
import net.linlan.dsh.cases.task.submit.node.SubmissionRootNode;
import net.linlan.dsh.cases.orchestration.CasePipeline;
import net.linlan.dsh.cases.orchestration.CasePipelineFactory;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

/**
 * 任务提交策略树工厂。
 *
 * <p>调用流程：SubmissionRootNode → ProfileResolutionNode → ToolResolutionNode → PermissionCheckNode → EnqueueNode。</p>
 * <p>示例：提交一条任务后，先解析执行画像，再校验权限，最后进入待执行队列。</p>
 *
 */
@Service
public class SubmitHarnessTaskFactory
        implements CasePipelineFactory<SubmitTaskRequestDTO, SubmitTaskDynamicContext, SubmitTaskResponseDTO> {

    @Autowired
    @Qualifier("submitTaskRootNode")
    private SubmissionRootNode rootNode;

    /**
     * 处理strategy Handler。
     * 流程：读取命令 → 执行用例编排 → 返回结果。
     * @return 方法执行结果。
     */
    public CasePipeline<SubmitTaskRequestDTO, SubmitTaskDynamicContext, SubmitTaskResponseDTO> pipeline() {
        return CasePipeline.of("Submit harness task", rootNode, SubmitTaskDynamicContext::new);
    }
}
