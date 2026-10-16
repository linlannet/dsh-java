package net.linlan.dsh.domain.agent.team.model.entity;

import java.time.Instant;
import java.util.Objects;

/**
 * TeamMessage 表示团队消息实体。
 *
 */
public record TeamMessage(
        String teamId,
        String fromAgent,
        String toAgent,
        String content,
        Instant createdAt
) {
    public TeamMessage {
        Objects.requireNonNull(teamId, "teamId 不能为空");
        Objects.requireNonNull(fromAgent, "fromAgent 不能为空");
        Objects.requireNonNull(toAgent, "toAgent 不能为空");
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }
}
