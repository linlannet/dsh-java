package net.linlan.dsh.trigger.http.command;

import net.linlan.dsh.api.dto.GoalCommandRequestDTO;
import net.linlan.dsh.api.dto.GoalViewResponseDTO;
import net.linlan.dsh.api.IGoalCommandApi;
import net.linlan.dsh.api.response.Response;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 目标命令控制器。
 * <p>
 * 执行流程：
 * 1. 接收前端动作请求；
 * 2. 调用命令适配器推进目标状态；
 * 3. 返回最新目标快照。
 *
 * <p>调用流程：Controller → API 适配器 → 用例/领域命令服务 → 返回响应。</p>
 * <p>示例：POST 请求携带 JSON，命令服务更新状态后返回最新视图。</p>
 *
 */
@RestController
@RequestMapping("/api/harness/goals")
public class GoalCommandController {

    private final IGoalCommandApi goalCommandApi;

    /**
     * 处理目标 命令 Controller。
     * 流程：解析请求参数 → 调用应用门面 → 返回响应。
     * @param goalCommandApi 目标 命令 API参数。
     * @return 方法执行结果。
     */
    public GoalCommandController(IGoalCommandApi goalCommandApi) {
        this.goalCommandApi = goalCommandApi;
    }

    /**
     * 处理apply。
     * 流程：解析请求参数 → 调用应用门面 → 返回响应。
     * @param sessionId 会话 ID。
     * @param request 请求对象。
     * @return 方法执行结果。
     */
    @PostMapping("/{sessionId}")
    public Response<GoalViewResponseDTO> apply(@PathVariable("sessionId") String sessionId, @RequestBody GoalCommandRequestDTO request) {
        return goalCommandApi.apply(sessionId, request);
    }
}
