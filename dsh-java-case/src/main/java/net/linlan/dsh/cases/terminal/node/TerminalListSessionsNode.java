package net.linlan.dsh.cases.terminal.node;

import net.linlan.dsh.cases.orchestration.AbstractStrategyRouter;
import net.linlan.dsh.cases.orchestration.NoContext;
import net.linlan.dsh.cases.orchestration.StrategyHandler;
import net.linlan.dsh.api.dto.TerminalSessionResponseDTO;
import net.linlan.dsh.domain.terminal.ITerminalQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.linlan.dsh.domain.terminal.model.valobj.TerminalSessionSnapshotVO;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 终端会话列表节点。
 * <p>流程：接收空请求 → 调用终端领域查询服务列出全部快照 → 转换为会话 DTO 列表。</p>
 * <p>数据示例：无入参；返回 {@code [{sessionId: "term-001", name: "build", status: "RUNNING"}]}。</p>
 */
@Service
public class TerminalListSessionsNode
        extends AbstractStrategyRouter<Void, NoContext, List<TerminalSessionResponseDTO>> {

    private static final Logger log = LoggerFactory.getLogger(TerminalListSessionsNode.class);

    private final ITerminalQueryService terminalQueryService;

    public TerminalListSessionsNode(ITerminalQueryService terminalQueryService) {
        this.terminalQueryService = terminalQueryService;
    }

    /**
     * 执行当前节点核心逻辑：校验输入、调用领域服务并返回输出。
     */
    @Override
    protected List<TerminalSessionResponseDTO> doApply(Void request, NoContext context) {
        var result = terminalQueryService.list().stream().map(this::toResponse).toList();
        log.debug("[Terminal] 终端列表查询完成，count={}", result.size());
        return result;
    }

    /**
     * 返回下一执行节点；单节点链路返回默认终止处理。
     */
    @Override
    public StrategyHandler<Void, NoContext, List<TerminalSessionResponseDTO>> getNext(Void request, NoContext context) {
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
