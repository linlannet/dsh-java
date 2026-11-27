package net.linlan.dsh.domain.session;

import net.linlan.dsh.domain.session.event.model.entity.SessionLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * 冷会话查询入口：预备 Session 对象后仅返回只读句柄。
 */
@Service
public class SessionQueryService {

    private final ISessionEventLogService eventLogService;
    private final CompactionSummaryProjection summaryProjection;

    @Autowired
    public SessionQueryService(ISessionEventLogService eventLogService) {
        this(eventLogService, new CompactionSummaryProjection());
    }

    SessionQueryService(ISessionEventLogService eventLogService,
                        CompactionSummaryProjection summaryProjection) {
        this.eventLogService = Objects.requireNonNull(eventLogService, "eventLogService");
        this.summaryProjection = Objects.requireNonNull(summaryProjection, "summaryProjection");
    }

    public SessionHandle open(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId must not be blank");
        }
        SessionLog session = new SessionLog(sessionId, eventLogService.getHeader(sessionId));
        session.restore(eventLogService.readAll(sessionId));
        return new SessionHandle(session, summaryProjection);
    }
}
