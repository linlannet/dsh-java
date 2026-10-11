package net.linlan.dsh.cases.terminal.node;

import net.linlan.dsh.cases.orchestration.AbstractStrategyRouter;
import net.linlan.dsh.cases.orchestration.NoContext;
import net.linlan.dsh.cases.orchestration.StrategyHandler;
import net.linlan.dsh.cases.support.CaseGuard;
import net.linlan.dsh.api.dto.TerminalSendRequestDTO;
import net.linlan.dsh.api.dto.TerminalSendResponseDTO;
import net.linlan.dsh.domain.terminal.ITerminalCommandService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.linlan.dsh.domain.terminal.model.valobj.TerminalSendResultVO;
import org.springframework.stereotype.Service;

/**
 * 终端命令发送节点。
 * <p>流程：校验请求和 sessionId → 将命令和超时交给终端领域命令服务 → 将执行结果转换为 DTO。</p>
 * <p>数据示例：传入 {@code command=ls -la, timeoutMs=3000}；
 * 返回 {@code completed=true, exitCode=0, durationMs=18}。</p>
 */
@Service
public class TerminalSendNode extends AbstractStrategyRouter<TerminalSendNode.Input, NoContext, TerminalSendResponseDTO> {

    private static final Logger log = LoggerFactory.getLogger(TerminalSendNode.class);

    private final ITerminalCommandService terminalCommandService;

    public TerminalSendNode(ITerminalCommandService terminalCommandService) {
        this.terminalCommandService = terminalCommandService;
    }

    /**
     * 执行当前节点核心逻辑：校验输入、调用领域服务并返回输出。
     */
    @Override
    protected TerminalSendResponseDTO doApply(Input input, NoContext context) {
        CaseGuard.requireNonNull(input.request(), "terminal send command must not be null");
        CaseGuard.requireSessionId(input.sessionId());
        var result = terminalCommandService.send(
                input.sessionId(), input.request().command(), input.request().timeoutMs()
        );
        log.info("[Terminal] 终端命令执行完成，sessionId={} timeoutMs={} completed={} exitCode={} durationMs={}",
                result.sessionId(), input.request().timeoutMs(), result.completed(),
                result.exitCode(), result.durationMs());
        return toResponse(result);
    }

    /**
     * 返回下一执行节点；单节点链路返回默认终止处理。
     */
    @Override
    public StrategyHandler<Input, NoContext, TerminalSendResponseDTO> getNext(
            Input input, NoContext context) {
        return defaultStrategyHandler;
    }

    public record Input(String sessionId, TerminalSendRequestDTO request) {
    }

    /**
     * 将领域层结果对象转换为用例层 DTO。
     */
    private TerminalSendResponseDTO toResponse(TerminalSendResultVO result) {
        return new TerminalSendResponseDTO(
                result.sessionId(), result.command(), result.output(), result.completed(),
                result.truncated(), result.status().name(), result.exitCode(),
                result.durationMs(), result.completedAt()
        );
    }
}
