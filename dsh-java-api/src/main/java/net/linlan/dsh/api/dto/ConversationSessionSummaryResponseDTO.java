package net.linlan.dsh.api.dto;

import java.time.Instant;

/**
 * 会话摘要响应对象。
 * <p>
 * 字段遵循客户端契约 v1（docs/spec/0.2.2/contract/fixtures/session-list.json）：
 * {@code agentId / sessionId / title / lastMessage / modelCode / updatedAt / createdAt / workspaceId}。
 * {@code preview} 为历史字段，与 {@code lastMessage} 同值保留，避免旧客户端断裂。
 */
public record ConversationSessionSummaryResponseDTO(
        String sessionId,
        String title,
        String preview,
        String status,
        Instant createdAt,
        String agentId,
        String workspaceId,
        String lastMessage,
        String modelCode,
        Instant updatedAt
) {
    /**
     * 兼容构造：无契约新增字段时，lastMessage 取 preview，modelCode / updatedAt 为空。
     */
    public ConversationSessionSummaryResponseDTO(String sessionId, String title, String preview, String status,
                                                 Instant createdAt, String agentId, String workspaceId) {
        this(sessionId, title, preview, status, createdAt, agentId, workspaceId, preview, null, null);
    }
}
