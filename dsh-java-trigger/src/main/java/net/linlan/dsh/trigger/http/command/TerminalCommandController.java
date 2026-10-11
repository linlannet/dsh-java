package net.linlan.dsh.trigger.http.command;

import net.linlan.dsh.api.dto.TerminalOpenRequestDTO;
import net.linlan.dsh.api.dto.TerminalSendRequestDTO;
import net.linlan.dsh.api.dto.TerminalSendResponseDTO;
import net.linlan.dsh.api.dto.TerminalSessionResponseDTO;
import net.linlan.dsh.api.ITerminalCommandApi;
import net.linlan.dsh.api.response.Response;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 终端命令控制器。
 * <p>
 * 执行流程：
 * 1. 接收前端打开或发送命令；
 * 2. 调用终端命令适配器；
 * 3. 返回结果响应。
 *
 * <p>调用流程：Controller → API 适配器 → 用例/领域命令服务 → 返回响应。</p>
 * <p>示例：POST 请求携带 JSON，命令服务更新状态后返回最新视图。</p>
 *
 */
@RestController
@RequestMapping("/api/harness/terminal")
public class TerminalCommandController {

    private final ITerminalCommandApi terminalCommandApi;

    /**
     * 处理终端 命令 Controller。
     * 流程：解析请求参数 → 调用应用门面 → 返回响应。
     * @param terminalCommandApi 终端 命令 API参数。
     * @return 方法执行结果。
     */
    public TerminalCommandController(ITerminalCommandApi terminalCommandApi) {
        this.terminalCommandApi = terminalCommandApi;
    }

    /**
     * 打开目标对象。
     * 流程：解析请求参数 → 调用应用门面 → 返回响应。
     * @param request 请求对象。
     * @return 方法执行结果。
     */
    @PostMapping("/sessions")
    public Response<TerminalSessionResponseDTO> open(@RequestBody TerminalOpenRequestDTO request) {
        return terminalCommandApi.open(request);
    }

    /**
     * 发送目标对象。
     * 流程：解析请求参数 → 调用应用门面 → 返回响应。
     * @param sessionId 会话 ID。
     * @param request 请求对象。
     * @return 方法执行结果。
     */
    @PostMapping("/sessions/{sessionId}/send")
    public Response<TerminalSendResponseDTO> send(@PathVariable("sessionId") String sessionId, @RequestBody TerminalSendRequestDTO request) {
        return terminalCommandApi.send(sessionId, request);
    }

    /**
     * 关闭目标对象。
     * 流程：解析请求参数 → 调用应用门面 → 返回响应。
     * @param sessionId 会话 ID。
     * @return 方法执行结果。
     */
    @PostMapping("/sessions/{sessionId}/close")
    public Response<TerminalSessionResponseDTO> close(@PathVariable("sessionId") String sessionId) {
        return terminalCommandApi.close(sessionId);
    }
}
