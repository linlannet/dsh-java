package net.linlan.dsh.infrastructure.dao.po;

import java.time.Instant;

public final class CollaborationPO {
    private CollaborationPO() {
    }

    public record RoomEventPO(String id, String roomId, Long seq, String taskId, String participantId, String digitalHumanId, String eventType, Instant occurredAt, String visibility, String payload, String traceId) {
    }

    public record ArtifactPO(String id, String roomId, String taskId, String producerId, String kind, String title, Integer version, String content, String traceId, Instant createdAt) {
    }

    public record TaskPO(String id, String roomId, String parentTaskId, String title, String instruction, String assignedTo, String requestedBy, String state, String dependsOn, String inputArtifacts, String outputArtifacts, String remoteSessionId, String traceId, String errorSummary, Instant createdAt, Instant updatedAt) {
    }

    public record ParticipantPO(String id, String roomId, String digitalHumanId, String roomRole, String presence, String activeTaskLabel, Instant joinedAt) {
    }

    public record RoomPO(String id, String workspaceId, String title, String objective, String orchestrationMode, String runState, Instant createdAt) {
    }
}

