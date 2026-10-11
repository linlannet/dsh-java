package net.linlan.dsh.trigger.http.command;

import net.linlan.dsh.api.dto.ResolveRuntimeApprovalRequestDTO;
import net.linlan.dsh.api.dto.ResolveRuntimeApprovalResponseDTO;
import net.linlan.dsh.api.dto.RuntimeApprovalResponseDTO;
import net.linlan.dsh.api.IRuntimeApprovalApi;
import net.linlan.dsh.api.response.Response;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 运行期工具审批 REST 端点。
 * <p>
 * agent 执行到需审批工具时实时挂起；前端轮询 {@code GET /runtime/pending}
 * 拿到待决条目，用户选择后 {@code POST /runtime/{approvalId}/resolve} 提交裁决，
 * 挂起的 agent 随即继续。对齐原版 TS 的 allow-once / allow-session / deny / cancel。
 * <p>调用流程：Controller → API 适配器 → 用例/领域命令服务 → 返回响应。</p>
 * <p>示例：POST 请求携带 JSON，命令服务更新状态后返回最新视图。</p>
 */
@RestController
@RequestMapping("/api/harness/approvals/runtime")
public class RuntimeApprovalController {

    private final IRuntimeApprovalApi runtimeApprovalApi;

    /**
     * 运行time Approval Controller。
     */
    public RuntimeApprovalController(IRuntimeApprovalApi runtimeApprovalApi) {
        this.runtimeApprovalApi = runtimeApprovalApi;
    }

    /** 当前待决的运行期审批列表。 */
    @GetMapping("/pending")
    public Response<List<RuntimeApprovalResponseDTO>> listPending() {
        return runtimeApprovalApi.listPending();
    }

    /**
     * 提交裁决。
     * <p>body: {@code {"verdict": "ALLOW_ONCE" | "ALLOW_SESSION" | "DENY" | "CANCEL"}}
     */
    @PostMapping("/{approvalId}/resolve")
    public Response<ResolveRuntimeApprovalResponseDTO> resolve(
            @PathVariable("approvalId") String approvalId,
            @RequestBody Map<String, String> body
    ) {
        return runtimeApprovalApi.resolve(
                approvalId,
                new ResolveRuntimeApprovalRequestDTO(body.getOrDefault("verdict", "DENY"))
        );
    }
}
