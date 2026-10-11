package net.linlan.dsh.domain.terminal.model.valobj;

import java.time.Instant;

/**
 * 终端会话快照。
 * <p>
 * 执行流程：
 * 1. 终端服务创建或刷新会话；
 * 2. 快照用于 API 返回；
 * 3. 前端只读展示状态与输出摘要。
 *
 * <p>调用流程：打开会话 → 发送命令 → 读取输出 → 关闭会话。</p>
 * <p>示例：发送 pwd 后读取标准输出，超时后可关闭终端会话。</p>
 *
 */
public record TerminalSessionSnapshotVO(
        String sessionId,
        String name,
        String cwd,
        TerminalSessionStatus status,
        Instant createdAt,
        Instant updatedAt,
        Integer exitCode,
        String lastMessage,
        int outputChars
) {
}
