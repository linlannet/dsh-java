package net.linlan.dsh.trigger.service.query;

import net.linlan.dsh.api.dto.PendingApprovalResponseDTO;
import net.linlan.dsh.api.IHarnessApprovalQueryApi;
import net.linlan.dsh.api.response.Response;
import net.linlan.dsh.cases.task.IPendingApprovalQueryCase;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 审批中心查询适配器。
 * <p>
 * 执行流程：
 * 1. 调用领域查询服务获取待审批会话；
 * 2. 将领域 VO 转为前端响应对象；
 * 3. 返回给控制器展示。
 *
 * <p>调用流程：Controller/DTO → 应用门面 → 用例；响应对象由用例结果转换。</p>
 * <p>示例：DTO 只做协议隔离，不承载领域规则。</p>
 *
 */
@Service
public class HarnessApprovalQueryApi implements IHarnessApprovalQueryApi {

    private final IPendingApprovalQueryCase approvalQueryCase;

    /**
     * 处理Harness 审批 Query API。
     * 流程：转换请求 → 调用用例或领域服务 → 转换响应。
     * @param approvalQueryService 审批 Query Service参数。
     * @return 方法执行结果。
     */
    public HarnessApprovalQueryApi(IPendingApprovalQueryCase approvalQueryCase) {
        this.approvalQueryCase = approvalQueryCase;
    }

    /**
     * 查询待审批列表。
     * <p>
     * 执行流程：
     * 1. 按 limit 获取待审批会话 VO；
     * 2. 映射为 `PendingApprovalResponseDTO`；
     * 3. 返回给前端审批中心。
     */
    public Response<List<PendingApprovalResponseDTO>> listPendingApprovals(int limit) {
        return execute(() -> approvalQueryCase.listPendingApprovals(limit));
    }

    private <T> Response<T> execute(java.util.function.Supplier<T> action) {
        try {
            return Response.success(action.get());
        } catch (IllegalArgumentException exception) {
            return Response.invalidArgument(exception.getMessage());
        } catch (RuntimeException exception) {
            return Response.internalError(exception.getMessage());
        }
    }
}
