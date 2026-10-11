package net.linlan.dsh.cases.terminal.node;

import net.linlan.dsh.cases.orchestration.AbstractStrategyRouter;
import net.linlan.dsh.cases.orchestration.NoContext;
import net.linlan.dsh.cases.orchestration.StrategyHandler;
import net.linlan.dsh.cases.support.CaseGuard;
import net.linlan.dsh.api.dto.TerminalSessionResponseDTO;
import net.linlan.dsh.domain.terminal.ITerminalCommandService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.linlan.dsh.domain.terminal.model.valobj.TerminalSessionSnapshotVO;
import org.springframework.stereotype.Service;

/**
 * 终端关闭节点。
 * <p>流程：校验 sessionId → 调用终端领域命令服务关闭会话 → 将快照转换为会话 DTO。</p>
 * <p>数据示例：传入 {@code sessionId=term-001}；返回 {@code status=CLOSED, exitCode=0}。</p>
 */
@Service
public class TerminalCloseNode extends AbstractStrategyRouter<String, NoContext, TerminalSessionResponseDTO> {

    private static final Logger log = LoggerFactory.getLogger(TerminalCloseNode.class);

    private final ITerminalCommandService terminalCommandService;

    public TerminalCloseNode(ITerminalCommandService terminalCommandService) {
        this.terminalCommandService = terminalCommandService;
    }

    /**
     * 执行当前节点核心逻辑：校验输入、调用领域服务并返回输出。
     */
    @Override
    protected TerminalSessionResponseDTO doApply(String sessionId, NoContext context) {
        CaseGuard.requireSessionId(sessionId);
        var result = terminalCommandService.close(sessionId);
        log.info("[Terminal] 终端关闭完成，sessionId={} status={}", result.sessionId(), result.status());
        return toResponse(result);
    }

    /**
     * 返回下一执行节点；单节点链路返回默认终止处理。
     */
    @Override
    public StrategyHandler<String, NoContext, TerminalSessionResponseDTO> getNext(String sessionId, NoContext context) {
        return defaultStrategyHandler;
    }

    /**
     * 将领域层结果对象转换为用例层 DTO。
     */
    private TerminalSessionResponseDTO toResponse(TerminalSessionSnapshotVO snapshot) {
        return new TerminalSessionResponseDTO(
                snapshot.sessionId(), snapshot.name(), snapshot.cwd(), snapshot.status().name(),
                snapshot.createdAt(), snapshot.updatedAt(), snapshot.exitCode(), snapshot.lastMessage(),
                snapshot.outputChars()
        );
    }
}
