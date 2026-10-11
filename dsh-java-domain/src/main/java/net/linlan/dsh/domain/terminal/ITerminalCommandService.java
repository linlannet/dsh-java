package net.linlan.dsh.domain.terminal;

import net.linlan.dsh.domain.terminal.model.valobj.TerminalSendResultVO;
import net.linlan.dsh.domain.terminal.model.valobj.TerminalSessionSnapshotVO;

/**
 * 终端命令服务接口。
 * <p>
 * 执行流程：
 * 1. 上层提交打开、发送、关闭命令；
 * 2. 领域层校验参数；
 * 3. 代理到底层终端端口执行。
 *
 * <p>调用流程：打开会话 → 发送命令 → 读取输出 → 关闭会话。</p>
 * <p>示例：发送 pwd 后读取标准输出，超时后可关闭终端会话。</p>
 *
 */
public interface ITerminalCommandService {

    TerminalSessionSnapshotVO open(String name, String cwd);

    TerminalSendResultVO send(String sessionId, String command, long timeoutMs);

    TerminalSessionSnapshotVO close(String sessionId);
}
