package net.linlan.dsh.domain.session.checkpoint.model.valobj;

import java.time.Instant;
import java.util.Objects;

public record CheckpointSnapshot(
        String sessionId,
        long stepIndex,
        String reason,
        String contextSnapshotJson,
        Instant createdAt
) {
    public CheckpointSnapshot {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    public static CheckpointSnapshot of(String sessionId, long stepIndex, String reason, String contextSnapshotJson) {
        return new CheckpointSnapshot(sessionId, stepIndex, reason, contextSnapshotJson, Instant.now());
    }
}
