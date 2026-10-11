package net.linlan.dsh.domain.terminal.model.valobj;

import java.time.Instant;

/**
 * 发送命令后的结果。
 * <p>
 * 执行流程：
 * 1. 写入命令到会话 stdin；
 * 2. 收集新输出片段；
 * 3. 返回执行状态与耗时信息。
 *
 * <p>调用流程：打开会话 → 发送命令 → 读取输出 → 关闭会话。</p>
 * <p>示例：发送 pwd 后读取标准输出，超时后可关闭终端会话。</p>
 *
 */
public record TerminalSendResultVO(
        String sessionId,
        String command,
        String output,
        boolean completed,
        boolean truncated,
        TerminalSessionStatus status,
        Integer exitCode,
        long durationMs,
        Instant completedAt
) {
}
