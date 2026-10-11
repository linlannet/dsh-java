package net.linlan.dsh.trigger.http.query;

import net.linlan.dsh.api.dto.GoalViewResponseDTO;
import net.linlan.dsh.api.IGoalQueryApi;
import net.linlan.dsh.api.response.Response;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 目标查询控制器。
 * <p>
 * 执行流程：
 * 1. 接收前端查询请求；
 * 2. 调用查询适配器读取当前目标；
 * 3. 直接返回响应体。
 *
 * <p>调用流程：Controller → API 适配器 → 查询服务 → 数据库或事件日志。</p>
 * <p>示例：GET 请求使用查询参数过滤，只读返回视图对象。</p>
 *
 */
@RestController
@RequestMapping("/api/harness/goals")
public class GoalQueryController {

    private final IGoalQueryApi goalQueryApi;

    /**
     * 处理目标 Query Controller。
     * 流程：解析请求参数 → 调用应用门面 → 返回响应。
     * @param goalQueryApi 目标 Query API参数。
     * @return 方法执行结果。
     */
    public GoalQueryController(IGoalQueryApi goalQueryApi) {
        this.goalQueryApi = goalQueryApi;
    }

    /**
     * 查询会话当前目标。
     * <p>
     * 执行流程：
     * 1. 接收 sessionId；
     * 2. 查询目标快照；
     * 3. 返回给前端。
     */
    @GetMapping("/{sessionId}")
    public Response<GoalViewResponseDTO> getGoal(@PathVariable("sessionId") String sessionId) {
        return goalQueryApi.getGoal(sessionId);
    }
}
