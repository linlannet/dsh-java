package net.linlan.dsh.api.dto;

import java.time.Instant;

/**
 * 终端会话响应。
 * <p>
 * 执行流程：
 * 1. 读取领域快照；
 * 2. 映射为前端可见字段；
 * 3. 返回会话状态与输出摘要。
 *
 * <p>调用流程：Controller/DTO → 应用门面 → 用例；响应对象由用例结果转换。</p>
 * <p>示例：DTO 只做协议隔离，不承载领域规则。</p>
 *
 */
public record TerminalSessionResponseDTO(
        String sessionId,
        String name,
        String cwd,
        String status,
        Instant createdAt,
        Instant updatedAt,
        Integer exitCode,
        String lastMessage,
        int outputChars
) {

}
