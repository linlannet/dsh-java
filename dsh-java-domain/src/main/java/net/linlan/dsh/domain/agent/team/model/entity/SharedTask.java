package net.linlan.dsh.domain.agent.team.model.entity;

import java.time.Instant;
import java.util.Objects;

/**
 * SharedTask 表示共享任务板上的任务实体。
 *
 */
public record SharedTask(
        String taskId,
        String teamId,
        String title,
        String status,
        String assignee,
        Instant createdAt
) {
    public SharedTask {
        Objects.requireNonNull(taskId, "taskId 不能为空");
        Objects.requireNonNull(teamId, "teamId 不能为空");
        status = status == null ? "open" : status;
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }
}
