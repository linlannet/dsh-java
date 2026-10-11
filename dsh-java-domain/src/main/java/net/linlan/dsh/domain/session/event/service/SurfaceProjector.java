package net.linlan.dsh.domain.session.event.service;

import net.linlan.dsh.domain.session.event.model.entity.SessionEvent;
import net.linlan.dsh.domain.session.event.model.valobj.SessionEventType;
import net.linlan.dsh.domain.session.event.model.valobj.SurfaceIntent;
import net.linlan.dsh.domain.session.event.model.valobj.SurfaceOp;
import net.linlan.dsh.domain.session.projection.adapter.port.ProjectionProvider;
import net.linlan.dsh.domain.session.projection.model.valobj.ProjectionType;

import java.util.ArrayList;
import java.util.List;

/**
 * SurfaceProjector 表示表面消息投影提供者。
 * <p>实现 {@link ProjectionProvider} 契约，支持增量合流。
 */
public final class SurfaceProjector implements ProjectionProvider<List<SurfaceProjector.SurfaceNode>> {

    /**
     * SurfaceNode 表示SurfaceNode。
     */
    public record SurfaceNode(long seq, SessionEvent event) {}

    @Override
    public ProjectionType type() {
        return ProjectionType.SURFACE;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Class<List<SurfaceNode>> projectionClass() {
        return (Class) List.class;
    }

    @Override
    public List<SurfaceNode> initial() {
        return List.of();
    }

    @Override
    public List<SurfaceNode> applyIncremental(List<SurfaceNode> current, SessionEvent event) {
        if (!event.isSurfaceEvent()) {
            return current;
        }
        SurfaceIntent intent = event.surfaceIntent();
        if (intent == null) {
            var next = new ArrayList<>(current);
            next.add(new SurfaceNode(event.seq(), event));
            return List.copyOf(next);
        }
        if (intent.surfaceOp() instanceof SurfaceOp.Append) {
            var next = new ArrayList<>(current);
            next.add(new SurfaceNode(event.seq(), event));
            return List.copyOf(next);
        }
        if (intent.surfaceOp() instanceof SurfaceOp.Replace r) {
            var next = new ArrayList<>(current);
            next.removeIf(node -> node.seq() >= r.start() && node.seq() <= r.end());
            next.add(new SurfaceNode(event.seq(), event));
            return List.copyOf(next);
        }
        return current;
    }

    /**
     * 处理project。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param events 事件列表。
     * @return 领域处理结果。
     */
    public List<SurfaceNode> project(List<SessionEvent> events) {
        List<SurfaceNode> surface = initial();
        for (SessionEvent event : events) {
            surface = applyIncremental(surface, event);
        }
        return surface;
    }

    /**
     * 处理extractChatMessages消息列表。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param events 事件列表。
     * @return 领域处理结果。
     */
    public List<SessionEvent> extractChatMessages(List<SessionEvent> events) {
        List<SurfaceNode> nodes = project(events);
        return nodes.stream()
                .map(SurfaceNode::event)
                .filter(e -> e.type() == SessionEventType.USER_MESSAGE
                        || e.type() == SessionEventType.ASSISTANT_MESSAGE)
                .toList();
    }
}
