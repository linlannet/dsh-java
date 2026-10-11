package net.linlan.dsh.trigger.http.query;

import net.linlan.dsh.api.dto.ConversationMessageResponseDTO;
import net.linlan.dsh.api.dto.ConversationSessionSummaryResponseDTO;
import net.linlan.dsh.api.IHarnessConsoleQueryApi;
import net.linlan.dsh.api.response.Response;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 面向 Web 控制台暴露会话查询端点。
 *
 * <p>调用流程：Controller → API 适配器 → 查询服务 → 数据库或事件日志。</p>
 * <p>示例：GET 请求使用查询参数过滤，只读返回视图对象。</p>
 *
 */
@RestController
@RequestMapping("/api/harness/console")
public class HarnessConsoleQueryController {

    private final IHarnessConsoleQueryApi harnessConsoleQueryApi;

    /**
     * 处理Harness Console Query Controller。
     * 流程：解析请求参数 → 调用应用门面 → 返回响应。
     * @param harnessConsoleQueryApi harness Console Query API参数。
     * @return 方法执行结果。
     */
    public HarnessConsoleQueryController(IHarnessConsoleQueryApi harnessConsoleQueryApi) {
        this.harnessConsoleQueryApi = harnessConsoleQueryApi;
    }

    /**
     * 获取Mapping。
     */
    @GetMapping("/sessions")
    public Response<List<ConversationSessionSummaryResponseDTO>> listRecentSessions(
            @RequestParam(name = "limit", defaultValue = "20") int limit,
            @RequestParam(name = "offset", defaultValue = "0") int offset) {
        return harnessConsoleQueryApi.listRecentSessions(limit, offset);
    }

    /**
     * 获取Mapping。
     */
    @GetMapping("/sessions/{sessionId}/messages")
    public Response<List<ConversationMessageResponseDTO>> listMessages(@PathVariable("sessionId") String sessionId) {
        return harnessConsoleQueryApi.listMessages(sessionId);
    }
}
