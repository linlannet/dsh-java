package net.linlan.dsh.cases.session;

import net.linlan.dsh.domain.session.ISessionEventLogService;
import net.linlan.dsh.domain.session.event.model.entity.SessionEvent;
import net.linlan.dsh.domain.session.event.model.entity.SessionHeader;
import net.linlan.dsh.domain.session.event.model.entity.SessionLog;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 创建共享 fork：父前缀由调用方显式提供，并先于返回的 Session 对象落盘。
 */
@Service
public class SessionForkService {

    private final ISessionEventLogService eventLogService;

    public SessionForkService(ISessionEventLogService eventLogService) {
        this.eventLogService = Objects.requireNonNull(eventLogService, "eventLogService");
    }

    public SessionLog fork(
            String parentSessionId,
            List<SessionEvent> parentPrefix,
            String forkSessionId,
            String cwd,
            String agentPreset
    ) {
        requireText(parentSessionId, "parentSessionId");
        requireText(forkSessionId, "forkSessionId");
        if (parentPrefix == null) {
            throw new IllegalArgumentException("SESSION_PARENT_PREFIX_REQUIRED");
        }
        List<SessionEvent> prefix = List.copyOf(parentPrefix);
        SessionHeader header = SessionHeader.forFork(
                forkSessionId, Instant.now(), cwd, parentSessionId, prefix.size(), agentPreset);
        eventLogService.createSession(header);
        if (!prefix.isEmpty()) {
            eventLogService.appendBatch(forkSessionId, prefix);
        }

        SessionLog fork = new SessionLog(forkSessionId, header);
        fork.restore(eventLogService.readAll(forkSessionId));
        return fork;
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
