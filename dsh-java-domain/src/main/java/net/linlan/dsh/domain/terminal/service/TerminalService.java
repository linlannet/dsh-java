package net.linlan.dsh.domain.terminal.service;

import net.linlan.dsh.domain.terminal.ITerminalCommandService;
import net.linlan.dsh.domain.terminal.ITerminalQueryService;
import net.linlan.dsh.domain.terminal.adapter.port.ITerminalPort;
import net.linlan.dsh.domain.terminal.model.valobj.TerminalReadResultVO;
import net.linlan.dsh.domain.terminal.model.valobj.TerminalSendResultVO;
import net.linlan.dsh.domain.terminal.model.valobj.TerminalSessionSnapshotVO;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 终端领域服务。
 * <p>
 * 执行流程：
 * 1. 校验会话参数；
 * 2. 调用终端端口执行本地进程操作；
 * 3. 返回会话快照和输出结果。
 *
 * <p>调用流程：打开会话 → 发送命令 → 读取输出 → 关闭会话。</p>
 * <p>示例：发送 pwd 后读取标准输出，超时后可关闭终端会话。</p>
 *
 */
@Service
public class TerminalService implements ITerminalCommandService, ITerminalQueryService {

    private final ITerminalPort terminalPort;

    /**
     * 处理终端 Service。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param terminalPort 终端 Port参数。
     * @return 方法执行结果。
     */
    public TerminalService(ITerminalPort terminalPort) {
        this.terminalPort = terminalPort;
    }

    @Override
    public TerminalSessionSnapshotVO open(String name, String cwd) {
        return terminalPort.open(name, cwd);
    }

    @Override
    public TerminalSendResultVO send(String sessionId, String command, long timeoutMs) {
        validateSessionId(sessionId);
        validateCommand(command);
        return terminalPort.send(sessionId, command, timeoutMs);
    }
    /**
     * 关闭目标对象。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param sessionId 会话 ID。
     * @return 方法执行结果。
     */


    @Override
    public TerminalSessionSnapshotVO close(String sessionId) {
        validateSessionId(sessionId);
        return terminalPort.close(sessionId);
    }

    @Override
    /**
     * 列出。
     */
    public TerminalReadResultVO read(String sessionId, int maxChars) {
        validateSessionId(sessionId);
        return terminalPort.read(sessionId, maxChars);
    }

    @Override
    public List<TerminalSessionSnapshotVO> list() {
        return terminalPort.list();
    }

    /**
     * 校验输入与业务规则；不通过时返回失败结果或抛出异常。
     */
    private static void validateSessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId must not be blank");
        }
    }

    /**
     * 校验输入与业务规则；不通过时返回失败结果或抛出异常。
     */
    private static void validateCommand(String command) {
        if (command == null || command.isBlank()) {
            throw new IllegalArgumentException("command must not be blank");
        }
    }
}
