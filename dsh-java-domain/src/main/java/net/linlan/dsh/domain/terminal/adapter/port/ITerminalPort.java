package net.linlan.dsh.domain.terminal.adapter.port;

import net.linlan.dsh.domain.terminal.model.valobj.TerminalReadResultVO;
import net.linlan.dsh.domain.terminal.model.valobj.TerminalSendResultVO;
import net.linlan.dsh.domain.terminal.model.valobj.TerminalSessionSnapshotVO;
import java.util.List;

/**
 * 终端会话端口。
 * <p>
 * 执行流程：
 * 1. 领域服务接收终端命令；
 * 2. 基础设施层负责启动/维护本地进程；
 * 3. 返回会话快照或输出结果。
 *
 * <p>调用流程：打开会话 → 发送命令 → 读取输出 → 关闭会话。</p>
 * <p>示例：发送 pwd 后读取标准输出，超时后可关闭终端会话。</p>
 *
 */
public interface ITerminalPort {

    /**
     * 打开一个终端会话。
     * <p>
     * 执行流程：
     * 1. 读取会话名称与工作目录；
     * 2. 启动本地 shell 进程；
     * 3. 返回会话快照。
     */
    TerminalSessionSnapshotVO open(String name, String cwd);

    /**
     * 向会话发送一条命令。
     * <p>
     * 执行流程：
     * 1. 定位指定会话；
     * 2. 通过 stdin 写入命令；
     * 3. 等待终端输出或超时。
     */
    TerminalSendResultVO send(String sessionId, String command, long timeoutMs);

    /**
     * 读取会话最新输出。
     * <p>
     * 执行流程：
     * 1. 定位指定会话；
     * 2. 截取尾部输出；
     * 3. 返回当前会话状态。
     */
    TerminalReadResultVO read(String sessionId, int maxChars);

    /**
     * 关闭一个会话。
     * <p>
     * 执行流程：
     * 1. 终止底层进程；
     * 2. 清理会话资源；
     * 3. 返回关闭后的快照。
     */
    TerminalSessionSnapshotVO close(String sessionId);

    /**
     * 列出当前会话。
     * <p>
     * 执行流程：
     * 1. 遍历会话注册表；
     * 2. 转成只读快照；
     * 3. 返回给查询方。
     */
    List<TerminalSessionSnapshotVO> list();
}
