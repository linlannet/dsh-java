package net.linlan.dsh.domain.terminal.model.valobj;

/**
 * 终端输出读取结果。
 * <p>
 * 执行流程：
 * 1. 读取当前会话缓冲区；
 * 2. 只返回最近输出片段；
 * 3. 保留是否截断的信息。
 *
 * <p>调用流程：打开会话 → 发送命令 → 读取输出 → 关闭会话。</p>
 * <p>示例：发送 pwd 后读取标准输出，超时后可关闭终端会话。</p>
 *
 */
public record TerminalReadResultVO(
        String sessionId,
        String content,
        boolean truncated,
        TerminalSessionStatus status,
        Integer exitCode
) {
}
