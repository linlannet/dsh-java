package net.linlan.dsh.cases.approval.factory;

import net.linlan.dsh.api.dto.ResolveRuntimeApprovalResponseDTO;
import net.linlan.dsh.api.dto.RuntimeApprovalResponseDTO;
import net.linlan.dsh.cases.approval.node.ListPendingApprovalsNode;
import net.linlan.dsh.cases.approval.node.ResolveRuntimeApprovalNode;
import net.linlan.dsh.cases.orchestration.CasePipeline;
import net.linlan.dsh.cases.orchestration.CasePipelineFactory;
import net.linlan.dsh.cases.orchestration.NoContext;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 构建运行期审批查询与裁决 Pipeline。
 * <p>流程：查询 → ListPendingApprovalsNode；裁决 → ResolveRuntimeApprovalNode。</p>
 * <p>数据示例：查询返回 {@code approval-001}；裁决输入为
 * {@code approvalId=approval-001, decision=ALLOW_ONCE}。</p>
 */
@Service
public class RuntimeApprovalPipelineFactory
        implements CasePipelineFactory<RuntimeApprovalResolveInput, NoContext, ResolveRuntimeApprovalResponseDTO> {

    private final ListPendingApprovalsNode listPendingApprovalsNode;
    private final ResolveRuntimeApprovalNode resolveRuntimeApprovalNode;

    public RuntimeApprovalPipelineFactory(
            ListPendingApprovalsNode listPendingApprovalsNode,
            ResolveRuntimeApprovalNode resolveRuntimeApprovalNode
    ) {
        this.listPendingApprovalsNode = listPendingApprovalsNode;
        this.resolveRuntimeApprovalNode = resolveRuntimeApprovalNode;
    }

    /**
     * 构建运行期待审批列表 Pipeline。
     */
    public CasePipeline<Void, NoContext, List<RuntimeApprovalResponseDTO>> listPendingPipeline() {
        return CasePipeline.of("List runtime approvals", listPendingApprovalsNode, () -> NoContext.INSTANCE);
    }

    /**
     * 构建运行期审批裁决 Pipeline。
     */
    public CasePipeline<RuntimeApprovalResolveInput, NoContext, ResolveRuntimeApprovalResponseDTO> resolvePipeline() {
        return CasePipeline.of("Resolve runtime approval", resolveRuntimeApprovalNode, () -> NoContext.INSTANCE);
    }

    /**
     * 构建默认执行 Pipeline，绑定首节点和上下文。
     */
    @Override
    public CasePipeline<RuntimeApprovalResolveInput, NoContext, ResolveRuntimeApprovalResponseDTO> pipeline() {
        return resolvePipeline();
    }
}
