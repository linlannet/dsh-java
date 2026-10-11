package net.linlan.dsh.trigger.service.query;

import net.linlan.dsh.api.dto.TerminalReadResponseDTO;
import net.linlan.dsh.api.dto.TerminalSessionResponseDTO;
import net.linlan.dsh.api.ITerminalQueryApi;
import net.linlan.dsh.api.response.Response;
import net.linlan.dsh.cases.terminal.ITerminalQueryCase;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 终端查询适配器。
 * <p>
 * 执行流程：
 * 1. 接收查询请求；
 * 2. 调用终端领域服务；
 * 3. 返回会话或输出结果。
 *
 * <p>调用流程：Controller/DTO → 应用门面 → 用例；响应对象由用例结果转换。</p>
 * <p>示例：DTO 只做协议隔离，不承载领域规则。</p>
 *
 */
@Service
public class TerminalQueryApi implements ITerminalQueryApi {

    private final ITerminalQueryCase terminalQueryCase;

    /**
     * 处理终端 Query API。
     * 流程：转换请求 → 调用用例或领域服务 → 转换响应。
     * @param terminalQueryService 终端 Query Service参数。
     * @return 方法执行结果。
     */
    public TerminalQueryApi(ITerminalQueryCase terminalQueryCase) {
        this.terminalQueryCase = terminalQueryCase;
    }

    /**
     * 列出Sessions。
     */
    public Response<List<TerminalSessionResponseDTO>> listSessions() {
        return execute(terminalQueryCase::listSessions);
    }

    /**
     * 读取目标对象。
     * 流程：转换请求 → 调用用例或领域服务 → 转换响应。
     * @param sessionId 会话 ID。
     * @param maxChars max Chars参数。
     * @return 方法执行结果。
     */
    public Response<TerminalReadResponseDTO> read(String sessionId, int maxChars) {
        requireSessionId(sessionId);
        return execute(() -> terminalQueryCase.read(sessionId, maxChars));
    }

    private void requireSessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId must not be blank");
        }
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
