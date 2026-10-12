package net.linlan.dsh.domain.session.projection.service;

import net.linlan.dsh.domain.session.event.model.entity.SessionEvent;
import net.linlan.dsh.domain.session.projection.adapter.port.ProjectionProvider;
import net.linlan.dsh.domain.session.projection.model.valobj.ProjectionType;

import java.util.ArrayList;
import java.util.List;

public final class TurnOutline implements ProjectionProvider<List<TurnOutline.TurnSummary>> {

    public record TurnSummary(long turn, String goal, String action, String result) {}

    @Override
    public ProjectionType type() {
        return ProjectionType.TURN_OUTLINE;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Class<List<TurnSummary>> projectionClass() {
        return (Class) List.class;
    }

    @Override
    public List<TurnSummary> initial() {
        return List.of();
    }

    @Override
    public List<TurnSummary> applyIncremental(List<TurnSummary> current, SessionEvent event) {
        if (event instanceof SessionEvent.TurnStart) {
            List<TurnSummary> next = new ArrayList<>(current);
            next.add(new TurnSummary(next.size() + 1, "", "", ""));
            return next;
        }
        if (event instanceof SessionEvent.TurnEnd && !current.isEmpty()) {
            List<TurnSummary> next = new ArrayList<>(current);
            int last = next.size() - 1;
            TurnSummary old = next.get(last);
            next.set(last, new TurnSummary(old.turn(), old.goal(), old.action(), "completed"));
            return next;
        }
        return current;
    }
}
