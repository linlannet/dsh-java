package net.linlan.dsh.domain.runtime.meter.service;

import net.linlan.dsh.domain.llm.model.valobj.TokenUsage;
import net.linlan.dsh.domain.runtime.meter.ITokenMeterService;
import net.linlan.dsh.domain.runtime.meter.adapter.repository.ITokenSnapshotRepository;
import net.linlan.dsh.domain.runtime.meter.model.valobj.ReplayContext;
import net.linlan.dsh.domain.runtime.meter.model.valobj.TokenSnapshot;
import net.linlan.dsh.domain.runtime.meter.model.valobj.TokenUsageSummaryVO;
import net.linlan.dsh.domain.session.ISessionEventLogService;
import net.linlan.dsh.domain.session.event.model.entity.SessionEvent;

import java.util.List;
import java.util.Objects;

/**
 * ReplayAwareTokenMeter 是重放感知的 Token 计量服务。
 * <p>重放模式下直接返回快照值不重算；实时模式下从快照点增量合流并持久化新快照。</p>
 */
public class ReplayAwareTokenMeter implements ITokenMeterService {

    private final ISessionEventLogService eventLogService;
    private final ITokenSnapshotRepository snapshotRepository;

    public ReplayAwareTokenMeter(
            ISessionEventLogService eventLogService,
            ITokenSnapshotRepository snapshotRepository
    ) {
        this.eventLogService = Objects.requireNonNull(eventLogService, "eventLogService");
        this.snapshotRepository = Objects.requireNonNull(snapshotRepository, "snapshotRepository");
    }

    @Override
    public TokenUsageSummaryVO summarize(String sessionId) {
        return summarize(sessionId, ReplayContext.live());
    }

    /**
     * 重放感知的 summarize。
     *
     * @param sessionId     会话 ID
     * @param replayContext 重放上下文
     * @return Token 用量汇总
     */
    public TokenUsageSummaryVO summarize(String sessionId, ReplayContext replayContext) {
        Objects.requireNonNull(replayContext, "replayContext");

        var snapshotOpt = snapshotRepository.load(sessionId);

        if (replayContext.isReplay() && snapshotOpt.isPresent()) {
            var snapshot = snapshotOpt.get();
            return new TokenUsageSummaryVO(
                    sessionId,
                    snapshot.modelCalls(),
                    snapshot.totalTokens()
            );
        }

        var snapshot = snapshotOpt.orElse(TokenSnapshot.initial());

        List<SessionEvent> events;
        if (snapshot.lastEventStepIndex() < 0) {
            events = eventLogService.readAll(sessionId);
        } else {
            events = eventLogService.readAfter(sessionId, snapshot.lastEventStepIndex());
        }

        TokenUsage deltaTokens = new TokenUsage(0, 0);
        long deltaModelCalls = 0;
        long maxSeq = snapshot.lastEventStepIndex();

        for (SessionEvent event : events) {
            if (event instanceof SessionEvent.AssistantMessage am && am.data().usage() != null) {
                deltaTokens = deltaTokens.plus(am.data().usage());
                deltaModelCalls++;
            }
            if (event.seq() > maxSeq) {
                maxSeq = event.seq();
            }
        }

        var newSnapshot = snapshot.advanceTo(maxSeq, deltaTokens, deltaModelCalls);
        snapshotRepository.save(sessionId, newSnapshot);

        return new TokenUsageSummaryVO(
                sessionId,
                newSnapshot.modelCalls(),
                newSnapshot.totalTokens()
        );
    }
}
