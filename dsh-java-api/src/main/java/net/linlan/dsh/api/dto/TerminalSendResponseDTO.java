package net.linlan.dsh.api.dto;

import java.time.Instant;

/**
 * 终端命令结果响应。
 * <p>
 * 执行流程：
 * 1. 读取命令执行结果；
 * 2. 映射为前端结构；
 * 3. 返回输出与终端状态。
 *
 * <p>调用流程：Controller/DTO → 应用门面 → 用例；响应对象由用例结果转换。</p>
 * <p>示例：DTO 只做协议隔离，不承载领域规则。</p>
 *
 */
public record TerminalSendResponseDTO(
        String sessionId,
        String command,
        String output,
        boolean completed,
        boolean truncated,
        String status,
        Integer exitCode,
        long durationMs,
        Instant completedAt
) {

}
