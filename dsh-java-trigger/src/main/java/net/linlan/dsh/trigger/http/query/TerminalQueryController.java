package net.linlan.dsh.trigger.http.query;

import net.linlan.dsh.api.dto.TerminalReadResponseDTO;
import net.linlan.dsh.api.dto.TerminalSessionResponseDTO;
import net.linlan.dsh.api.ITerminalQueryApi;
import net.linlan.dsh.api.response.Response;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 终端查询控制器。
 * <p>
 * 执行流程：
 * 1. 接收查询请求；
 * 2. 调用终端查询适配器；
 * 3. 返回会话列表或输出片段。
 *
 * <p>调用流程：Controller → API 适配器 → 查询服务 → 数据库或事件日志。</p>
 * <p>示例：GET 请求使用查询参数过滤，只读返回视图对象。</p>
 *
 */
@RestController
@RequestMapping("/api/harness/terminal")
public class TerminalQueryController {

    private final ITerminalQueryApi terminalQueryApi;

    /**
     * 处理终端 Query Controller。
     * 流程：解析请求参数 → 调用应用门面 → 返回响应。
     * @param terminalQueryApi 终端 Query API参数。
     * @return 方法执行结果。
     */
    public TerminalQueryController(ITerminalQueryApi terminalQueryApi) {
        this.terminalQueryApi = terminalQueryApi;
    }

    /**
     * 获取Mapping。
     */
    @GetMapping("/sessions")
    public Response<List<TerminalSessionResponseDTO>> listSessions() {
        return terminalQueryApi.listSessions();
    }

    /**
     * 获取Mapping。
     */
    @GetMapping("/sessions/{sessionId}/read")
    public Response<TerminalReadResponseDTO> read(@PathVariable("sessionId") String sessionId,
                                     @RequestParam(name = "maxChars", defaultValue = "8000") int maxChars) {
        return terminalQueryApi.read(sessionId, maxChars);
    }
}
