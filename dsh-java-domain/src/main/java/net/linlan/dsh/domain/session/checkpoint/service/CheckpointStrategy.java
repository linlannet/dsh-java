package net.linlan.dsh.domain.session.checkpoint.service;

import net.linlan.dsh.domain.session.checkpoint.adapter.repository.ICheckpointRepository;
import net.linlan.dsh.domain.session.checkpoint.model.valobj.CheckpointSnapshot;
import net.linlan.dsh.domain.session.event.model.entity.SessionEvent;
import net.linlan.dsh.domain.session.event.model.entity.SessionLog;

public final class CheckpointStrategy {

    public static final String REASON_BEFORE_MODEL_REQUEST = "before-model-request";
    public static final String REASON_BEFORE_TOOL_SIDE_EFFECT = "before-tool-side-effect";

    private final ICheckpointRepository repository;

    public CheckpointStrategy(ICheckpointRepository repository) {
        this.repository = repository;
    }

    public CheckpointSnapshot beforeModelRequest(SessionLog session) {
        CheckpointSnapshot snapshot = CheckpointSnapshot.of(
                session.sessionId(), session.lastSeq(),
                REASON_BEFORE_MODEL_REQUEST, serializeContext(session));
        repository.save(snapshot);
        return snapshot;
    }

    public CheckpointSnapshot beforeToolSideEffect(SessionLog session, SessionEvent.ToolCall toolCall) {
        CheckpointSnapshot snapshot = CheckpointSnapshot.of(
                session.sessionId(), session.lastSeq(),
                REASON_BEFORE_TOOL_SIDE_EFFECT, serializeContext(session));
        repository.save(snapshot);
        return snapshot;
    }

    private String serializeContext(SessionLog session) {
        return "{\"eventCount\":" + session.size() + ",\"lastSeq\":" + session.lastSeq() + "}";
    }
}
