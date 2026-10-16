package net.linlan.dsh.domain.session.checkpoint.service;

import net.linlan.dsh.domain.session.checkpoint.adapter.repository.ICheckpointRepository;
import net.linlan.dsh.domain.session.checkpoint.model.valobj.CheckpointRollback;
import net.linlan.dsh.domain.session.checkpoint.model.valobj.CheckpointSnapshot;
import net.linlan.dsh.domain.session.event.model.entity.SessionEvent;
import net.linlan.dsh.domain.session.event.model.entity.SessionLog;

import java.util.Optional;

public final class CheckpointStrategy {

    public static final String REASON_BEFORE_MODEL_REQUEST = "before-model-request";
    public static final String REASON_BEFORE_TOOL_SIDE_EFFECT = "before-tool-side-effect";
    public static final String REASON_BEFORE_TOOL_EXECUTION = "before-tool-execution";
    public static final String REASON_AFTER_TOOL_EXECUTION = "after-tool-execution";
    public static final String REASON_BEFORE_AGENT_TURN = "before-agent-turn";
    public static final String REASON_AFTER_AGENT_TURN = "after-agent-turn";

    private final ICheckpointRepository repository;

    public CheckpointStrategy(ICheckpointRepository repository) {
        this.repository = repository;
    }

    public CheckpointSnapshot beforeModelRequest(SessionLog session) {
        return writeCheckpoint(session, REASON_BEFORE_MODEL_REQUEST);
    }

    public CheckpointSnapshot beforeToolSideEffect(SessionLog session, SessionEvent.ToolCall toolCall) {
        return writeCheckpoint(session, REASON_BEFORE_TOOL_SIDE_EFFECT);
    }

    public CheckpointSnapshot beforeToolExecution(SessionLog session, String toolName) {
        return writeCheckpoint(session, REASON_BEFORE_TOOL_EXECUTION);
    }

    public CheckpointSnapshot afterToolExecution(SessionLog session, String toolName) {
        return writeCheckpoint(session, REASON_AFTER_TOOL_EXECUTION);
    }

    public CheckpointSnapshot beforeAgentTurn(SessionLog session) {
        return writeCheckpoint(session, REASON_BEFORE_AGENT_TURN);
    }

    public CheckpointSnapshot afterAgentTurn(SessionLog session) {
        return writeCheckpoint(session, REASON_AFTER_AGENT_TURN);
    }

    public CheckpointSnapshot checkpoint(SessionLog session, String reason) {
        return writeCheckpoint(session, reason);
    }

    public CheckpointRollback rollbackTo(String sessionId, long stepIndex) {
        Optional<CheckpointSnapshot> snapshot = repository.loadAtStep(sessionId, stepIndex);
        if (snapshot.isEmpty()) {
            return CheckpointRollback.notFound("sessionId=" + sessionId + ", stepIndex=" + stepIndex);
        }
        return CheckpointRollback.success(snapshot.get());
    }

    public CheckpointRollback rollbackToLatest(String sessionId, String reason) {
        Optional<CheckpointSnapshot> snapshot = repository.loadLatestByReason(sessionId, reason);
        if (snapshot.isEmpty()) {
            return CheckpointRollback.notFound("sessionId=" + sessionId + ", reason=" + reason);
        }
        return CheckpointRollback.success(snapshot.get());
    }

    public CheckpointRollback rollbackToLatest(String sessionId) {
        Optional<CheckpointSnapshot> snapshot = repository.loadLatest(sessionId);
        if (snapshot.isEmpty()) {
            return CheckpointRollback.notFound("sessionId=" + sessionId);
        }
        return CheckpointRollback.success(snapshot.get());
    }

    private CheckpointSnapshot writeCheckpoint(SessionLog session, String reason) {
        CheckpointSnapshot snapshot = CheckpointSnapshot.of(
                session.sessionId(), session.lastSeq(),
                reason, serializeContext(session));
        repository.save(snapshot);
        return snapshot;
    }

    private String serializeContext(SessionLog session) {
        return "{\"eventCount\":" + session.size() + ",\"lastSeq\":" + session.lastSeq() + "}";
    }
}
