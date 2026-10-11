package net.linlan.dsh.domain.terminal.model.valobj;

/**
 * 终端会话状态。
 * <p>
 * 执行流程：
 * 1. 后端进程启动后进入 running；
 * 2. 进程退出后进入 exited；
 * 3. 显式关闭后进入 closed。
 *
 * <p>调用流程：打开会话 → 发送命令 → 读取输出 → 关闭会话。</p>
 * <p>示例：发送 pwd 后读取标准输出，超时后可关闭终端会话。</p>
 *
 */
public enum TerminalSessionStatus {
    RUNNING,
    EXITED,
    CLOSED
}
