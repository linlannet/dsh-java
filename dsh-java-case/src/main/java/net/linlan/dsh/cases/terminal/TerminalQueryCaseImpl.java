package net.linlan.dsh.cases.terminal;

import net.linlan.dsh.api.dto.TerminalReadResponseDTO;
import net.linlan.dsh.api.dto.TerminalSessionResponseDTO;
import net.linlan.dsh.cases.terminal.factory.TerminalPipelineFactory;
import net.linlan.dsh.cases.terminal.node.TerminalReadNode;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 终端读操作编排实现。
 * <p>流程：按列表/读取目标选择 Pipeline → 执行终端查询节点 → 返回会话或输出 DTO。</p>
 * <p>数据示例：{@code read("term-001", 8000)} 返回 {@code content="total 0", truncated=false}。</p>
 */
@Service
public class TerminalQueryCaseImpl implements ITerminalQueryCase {
    private final TerminalPipelineFactory terminalPipelineFactory;

    public TerminalQueryCaseImpl(TerminalPipelineFactory terminalPipelineFactory) {
        this.terminalPipelineFactory = terminalPipelineFactory;
    }

    /**
     * 编排终端会话列表查询。
     */
    @Override
    public List<TerminalSessionResponseDTO> listSessions() {
        return terminalPipelineFactory.listPipeline().execute(null);
    }

    /**
     * 编排终端输出读取操作。
     */
    @Override
    public TerminalReadResponseDTO read(String sessionId, int maxChars) {
        return terminalPipelineFactory.readPipeline().execute(new TerminalReadNode.Input(sessionId, maxChars));
    }
}
