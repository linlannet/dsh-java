package net.linlan.dsh.domain.session;

import net.linlan.dsh.domain.model.entity.Message;
import net.linlan.dsh.domain.model.valobj.TextBlock;
import net.linlan.dsh.domain.session.event.model.entity.SessionEvent;
import net.linlan.dsh.domain.session.event.model.entity.SessionLog;
import net.linlan.dsh.domain.session.event.model.valobj.SurfaceOp;

import java.util.List;
import java.util.Optional;

/**
 * 从会话事件中的替换型摘要消息投影最近一次压缩结果。
 */
public final class CompactionSummaryProjection {

    private static final String SUMMARY_PREFIX = "[Compacted History]";

    public Optional<CompactionSummary> project(SessionLog session) {
        return project(session.events());
    }

    public Optional<CompactionSummary> project(List<SessionEvent> events) {
        CompactionSummary latest = null;
        for (SessionEvent event : events) {
            if (!(event instanceof SessionEvent.UserMessage userMessage)
                    || event.surfaceIntent() == null
                    || !(event.surfaceIntent().surfaceOp() instanceof SurfaceOp.Replace)) {
                continue;
            }
            SurfaceOp.Replace replace = (SurfaceOp.Replace) event.surfaceIntent().surfaceOp();
            Message message = userMessage.data().message();
            if (message == null || message.content() == null) {
                continue;
            }
            for (var block : message.content()) {
                if (block instanceof TextBlock text && text.text().startsWith(SUMMARY_PREFIX)) {
                    String summary = text.text().substring(SUMMARY_PREFIX.length()).strip();
                    latest = new CompactionSummary(summary, replace.start(), replace.end(), event.seq());
                }
            }
        }
        return Optional.ofNullable(latest);
    }

    public record CompactionSummary(String summary, long startSeq, long endSeq, long eventSeq) {
    }
}
