package net.linlan.dsh.cases.terminal.factory;

import net.linlan.dsh.cases.orchestration.CasePipeline;
import net.linlan.dsh.cases.orchestration.CasePipelineFactory;
import net.linlan.dsh.cases.orchestration.NoContext;
import net.linlan.dsh.api.dto.TerminalOpenRequestDTO;
import net.linlan.dsh.api.dto.TerminalReadResponseDTO;
import net.linlan.dsh.api.dto.TerminalSendRequestDTO;
import net.linlan.dsh.api.dto.TerminalSendResponseDTO;
import net.linlan.dsh.api.dto.TerminalSessionResponseDTO;
import net.linlan.dsh.cases.terminal.node.TerminalCloseNode;
import net.linlan.dsh.cases.terminal.node.TerminalListSessionsNode;
import net.linlan.dsh.cases.terminal.node.TerminalOpenNode;
import net.linlan.dsh.cases.terminal.node.TerminalReadNode;
import net.linlan.dsh.cases.terminal.node.TerminalSendNode;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 终端 Pipeline 工厂。
 * <p>流程：按打开、发送、关闭、列表、读取五个目标组装独立 Pipeline，每个 Pipeline 路由到对应终端节点。</p>
 * <p>数据示例：打开 {@code /tmp/demo} 返回 {@code status=RUNNING}；
 * 读取 {@code term-001, maxChars=8000} 返回终端输出。</p>
 */
@Service
public class TerminalPipelineFactory
        implements CasePipelineFactory<TerminalOpenRequestDTO, NoContext, TerminalSessionResponseDTO> {

    private final TerminalOpenNode terminalOpenNode;
    private final TerminalSendNode terminalSendNode;
    private final TerminalCloseNode terminalCloseNode;
    private final TerminalListSessionsNode terminalListSessionsNode;
    private final TerminalReadNode terminalReadNode;

    public TerminalPipelineFactory(
            TerminalOpenNode terminalOpenNode,
            TerminalSendNode terminalSendNode,
            TerminalCloseNode terminalCloseNode,
            TerminalListSessionsNode terminalListSessionsNode,
            TerminalReadNode terminalReadNode
    ) {
        this.terminalOpenNode = terminalOpenNode;
        this.terminalSendNode = terminalSendNode;
        this.terminalCloseNode = terminalCloseNode;
        this.terminalListSessionsNode = terminalListSessionsNode;
        this.terminalReadNode = terminalReadNode;
    }

    /**
     * 构建当前用例的执行 Pipeline，绑定首节点和上下文。
     */
    public CasePipeline<TerminalOpenRequestDTO, NoContext, TerminalSessionResponseDTO> openPipeline() {
        return CasePipeline.of("Open terminal", terminalOpenNode, () -> NoContext.INSTANCE);
    }

    /**
     * 构建当前用例的执行 Pipeline，绑定首节点和上下文。
     */
    public CasePipeline<TerminalSendNode.Input, NoContext, TerminalSendResponseDTO> sendPipeline() {
        return CasePipeline.of("Send terminal", terminalSendNode, () -> NoContext.INSTANCE);
    }

    /**
     * 构建当前用例的执行 Pipeline，绑定首节点和上下文。
     */
    public CasePipeline<String, NoContext, TerminalSessionResponseDTO> closePipeline() {
        return CasePipeline.of("Close terminal", terminalCloseNode, () -> NoContext.INSTANCE);
    }

    /**
     * 构建当前用例的执行 Pipeline，绑定首节点和上下文。
     */
    public CasePipeline<Void, NoContext, List<TerminalSessionResponseDTO>> listPipeline() {
        return CasePipeline.of("List terminals", terminalListSessionsNode, () -> NoContext.INSTANCE);
    }

    /**
     * 构建当前用例的执行 Pipeline，绑定首节点和上下文。
     */
    public CasePipeline<TerminalReadNode.Input, NoContext, TerminalReadResponseDTO> readPipeline() {
        return CasePipeline.of("Read terminal", terminalReadNode, () -> NoContext.INSTANCE);
    }

    /**
     * 构建默认执行 Pipeline，绑定首节点和上下文。
     */
    @Override
    public CasePipeline<TerminalOpenRequestDTO, NoContext, TerminalSessionResponseDTO> pipeline() {
        return openPipeline();
    }
}
