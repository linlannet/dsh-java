package net.linlan.dsh.domain.session.projection.service;

import net.linlan.dsh.domain.session.event.model.entity.SessionEvent;
import net.linlan.dsh.domain.session.projection.adapter.port.ProjectionProvider;
import net.linlan.dsh.domain.session.projection.model.valobj.ProjectionType;

public final class SessionStatistics implements ProjectionProvider<SessionStatistics.Stats> {

    public record Stats(long messageCount, long toolCallCount, long inputTokens, long outputTokens) {
        public Stats add(Stats other) {
            return new Stats(
                    messageCount + other.messageCount,
                    toolCallCount + other.toolCallCount,
                    inputTokens + other.inputTokens,
                    outputTokens + other.outputTokens);
        }
    }

    @Override
    public ProjectionType type() {
        return ProjectionType.STATISTICS;
    }

    @Override
    public Class<Stats> projectionClass() {
        return Stats.class;
    }

    @Override
    public Stats initial() {
        return new Stats(0, 0, 0, 0);
    }

    @Override
    public Stats applyIncremental(Stats current, SessionEvent event) {
        long msg = current.messageCount();
        long tool = current.toolCallCount();
        if (event instanceof SessionEvent.UserMessage || event instanceof SessionEvent.AssistantMessage) {
            msg++;
        }
        if (event instanceof SessionEvent.ToolCall) {
            tool++;
        }
        return new Stats(msg, tool, current.inputTokens(), current.outputTokens());
    }
}
