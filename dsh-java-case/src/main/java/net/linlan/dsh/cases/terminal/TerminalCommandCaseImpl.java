package net.linlan.dsh.cases.terminal;

import net.linlan.dsh.api.dto.TerminalOpenRequestDTO;
import net.linlan.dsh.api.dto.TerminalSendRequestDTO;
import net.linlan.dsh.api.dto.TerminalSendResponseDTO;
import net.linlan.dsh.api.dto.TerminalSessionResponseDTO;
import net.linlan.dsh.cases.terminal.factory.TerminalPipelineFactory;
import net.linlan.dsh.cases.terminal.node.TerminalReadNode;
import net.linlan.dsh.cases.terminal.node.TerminalSendNode;
import org.springframework.stereotype.Service;

/**
 * 终端写操作编排实现。
 * <p>流程：按打开/发送/关闭目标选择 Pipeline → 执行终端命令节点 → 返回会话或执行结果 DTO。</p>
 * <p>数据示例：发送 {@code ls -la} 后返回 {@code completed=true, exitCode=0}；
 * 关闭 {@code term-001} 后返回 {@code status=CLOSED}。</p>
 */
@Service
public class TerminalCommandCaseImpl implements ITerminalCommandCase {
    private final TerminalPipelineFactory terminalPipelineFactory;

    public TerminalCommandCaseImpl(TerminalPipelineFactory terminalPipelineFactory) {
        this.terminalPipelineFactory = terminalPipelineFactory;
    }

    /**
     * 编排终端会话打开操作。
     */
    @Override
    public TerminalSessionResponseDTO open(TerminalOpenRequestDTO command) {
        return terminalPipelineFactory.openPipeline().execute(command);
    }

    /**
     * 编排终端命令发送操作。
     */
    @Override
    public TerminalSendResponseDTO send(String sessionId, TerminalSendRequestDTO command) {
        return terminalPipelineFactory.sendPipeline().execute(new TerminalSendNode.Input(sessionId, command));
    }

    /**
     * 编排终端会话关闭操作。
     */
    @Override
    public TerminalSessionResponseDTO close(String sessionId) {
        return terminalPipelineFactory.closePipeline().execute(sessionId);
    }
}
