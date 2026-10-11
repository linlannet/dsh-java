package net.linlan.dsh.trigger.http.command;

import net.linlan.dsh.api.dto.ApprovalActionResponseDTO;
import net.linlan.dsh.api.IHarnessApprovalCommandApi;
import net.linlan.dsh.api.response.Response;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 审批中心命令控制器。
 * <p>
 * 执行流程：
 * 1. 接收前端审批动作请求；
 * 2. 调用命令适配器推进会话状态；
 * 3. 返回执行结果。
 *
 * <p>调用流程：Controller → API 适配器 → 用例/领域命令服务 → 返回响应。</p>
 * <p>示例：POST 请求携带 JSON，命令服务更新状态后返回最新视图。</p>
 *
 */
@RestController
@RequestMapping("/api/harness/approvals")
public class HarnessApprovalCommandController {

    private final IHarnessApprovalCommandApi harnessApprovalCommandApi;

    /**
     * 处理Harness 审批 命令 Controller。
     * 流程：解析请求参数 → 调用应用门面 → 返回响应。
     * @param harnessApprovalCommandApi harness 审批 命令 API参数。
     * @return 方法执行结果。
     */
    public HarnessApprovalCommandController(IHarnessApprovalCommandApi harnessApprovalCommandApi) {
        this.harnessApprovalCommandApi = harnessApprovalCommandApi;
    }

    /**
     * 执行审批通过。
     * <p>
     * 执行流程：
     * 1. 接收 sessionId；
     * 2. 交给命令适配器执行审批；
     * 3. 返回审批结果。
     */
    @PostMapping("/{sessionId}/approve")
    public Response<ApprovalActionResponseDTO> approve(@PathVariable("sessionId") String sessionId) {
        return harnessApprovalCommandApi.approve(sessionId);
    }
}
