package net.linlan.dsh.domain.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import net.linlan.dsh.domain.session.ISessionLogService;
import net.linlan.dsh.domain.session.registry.adapter.repository.IHarnessSessionRepository;
import net.linlan.dsh.domain.session.registry.model.aggregate.HarnessSessionAggregate;
import net.linlan.dsh.domain.shared.model.valobj.HarnessStatusEnumVO;
import net.linlan.dsh.domain.task.approval.model.valobj.ApprovalActionVO;
import net.linlan.dsh.domain.task.approval.service.ApprovalCommandService;
import net.linlan.dsh.domain.task.execution.model.valobj.HarnessExecutionResultVO;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ApprovalCommandServiceTest {

    @Test
    void approveMovesPendingSessionToQueuedAndExecutesIt() {
        HarnessSessionAggregate session = new HarnessSessionAggregate(
                "session-1", "workspace-1", "web", Instant.parse("2026-01-01T00:00:00Z"));
        session.changeStatus(HarnessStatusEnumVO.PENDING_APPROVAL);
        InMemorySessionRepository repository = new InMemorySessionRepository(session);
        InMemorySessionLog log = new InMemorySessionLog();
        ApprovalCommandService service = new ApprovalCommandService(
                repository, log, sessionId -> new HarnessExecutionResultVO(
                        sessionId, HarnessStatusEnumVO.COMPLETED, "done", Instant.now()));

        ApprovalActionVO result = service.approve("session-1");

        assertEquals("COMPLETED", result.status());
        assertEquals("done", result.message());
        assertEquals(HarnessStatusEnumVO.QUEUED, repository.saved.getStatus());
        assertEquals(1, log.approvalAcceptedCount);
    }

    @Test
    void approveRejectsSessionWhichIsNotPendingApproval() {
        HarnessSessionAggregate session = new HarnessSessionAggregate(
                "session-2", "workspace-1", "web", Instant.parse("2026-01-01T00:00:00Z"));
        ApprovalCommandService service = new ApprovalCommandService(
                new InMemorySessionRepository(session), new InMemorySessionLog(), sessionId -> {
                    throw new IllegalStateException("must not execute");
                });

        assertThrows(IllegalStateException.class, () -> service.approve("session-2"));
    }

    @Test
    void approveRejectsMissingSession() {
        ApprovalCommandService service = new ApprovalCommandService(
                new InMemorySessionRepository(null), new InMemorySessionLog(), sessionId -> {
                    throw new IllegalStateException("must not execute");
                });

        assertThrows(IllegalArgumentException.class, () -> service.approve("missing"));
    }

    private static final class InMemorySessionRepository implements IHarnessSessionRepository {

        private final HarnessSessionAggregate loaded;
        private HarnessSessionAggregate saved;

        private InMemorySessionRepository(HarnessSessionAggregate loaded) {
            this.loaded = loaded;
        }

        @Override
        public void save(HarnessSessionAggregate session) {
            this.saved = session;
        }

        @Override
        public Optional<HarnessSessionAggregate> findBySessionId(String sessionId) {
            return loaded == null ? Optional.empty() : Optional.of(loaded);
        }

        @Override
        public java.util.List<HarnessSessionAggregate> listRecent(int limit) {
            return loaded == null ? java.util.List.of() : java.util.List.of(loaded);
        }
    }

    private static final class InMemorySessionLog implements ISessionLogService {

        private int approvalAcceptedCount;

        @Override
        public void recordTaskSubmission(String sessionId, String taskId, String profileCode,
                String prompt, boolean approvalRequired, String approvalReason) {
        }

        @Override
        public void recordApprovalAccepted(String sessionId) {
            approvalAcceptedCount++;
        }

        @Override
        public void recordExecutionStarted(String sessionId, String resolvedModel) {
        }

        @Override
        public void recordExecutionCompleted(String sessionId, String modelCode, String assistantMessage) {
        }

        @Override
        public void recordExecutionFailed(String sessionId, String failureMessage) {
        }
    }
}
