package net.linlan.dsh.cases.approval;

import net.linlan.dsh.api.dto.ResolveRuntimeApprovalRequestDTO;
import net.linlan.dsh.api.dto.ResolveRuntimeApprovalResponseDTO;
import net.linlan.dsh.api.dto.RuntimeApprovalResponseDTO;
import net.linlan.dsh.cases.approval.factory.RuntimeApprovalPipelineFactory;
import net.linlan.dsh.cases.approval.factory.RuntimeApprovalResolveInput;
import net.linlan.dsh.domain.agent.model.valobj.RuntimeApprovalDecisionVO;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 运行期审批编排实现。
 * <p>流程：校验/归一化裁决 → 组装 Pipeline 输入 → 调用审批节点 → 返回 DTO。</p>
 * <p>数据示例：{@code verdict="allow-once"} 会被转换为 {@code ALLOW_ONCE}；
 * 无效值如 {@code maybe} 返回 {@code resolved=false, error="unknown verdict: maybe"}。</p>
 */
@Service
public class RuntimeApprovalCaseImpl implements IRuntimeApprovalCase {

    private final RuntimeApprovalPipelineFactory runtimeApprovalPipelineFactory;

    public RuntimeApprovalCaseImpl(RuntimeApprovalPipelineFactory runtimeApprovalPipelineFactory) {
        this.runtimeApprovalPipelineFactory = runtimeApprovalPipelineFactory;
    }

    /**
     * 编排运行期待审批列表查询。
     */
    @Override
    public List<RuntimeApprovalResponseDTO> listPending() {
        return runtimeApprovalPipelineFactory.listPendingPipeline().execute(null);
    }

    /**
     * 编排运行期审批裁决，归一化裁决值后执行 Pipeline。
     */
    @Override
    public ResolveRuntimeApprovalResponseDTO resolve(String approvalId, ResolveRuntimeApprovalRequestDTO request) {
        RuntimeApprovalDecisionVO decision;
        try {
            decision = RuntimeApprovalDecisionVO.valueOf(normalize(request.verdict()));
        } catch (RuntimeException exception) {
            return new ResolveRuntimeApprovalResponseDTO(false, null, "unknown verdict: " + request.verdict());
        }
        return runtimeApprovalPipelineFactory.resolvePipeline().execute(new RuntimeApprovalResolveInput(approvalId, decision));
    }

    /**
     * 处理当前用例步骤，完成必要校验并返回执行结果。
     */
    private String normalize(String verdict) {
        return (verdict == null ? "DENY" : verdict)
                .trim()
                .toUpperCase()
                .replace('-', '_');
    }
}
