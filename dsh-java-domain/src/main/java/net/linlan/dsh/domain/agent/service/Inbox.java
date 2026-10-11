package net.linlan.dsh.domain.agent.service;

import net.linlan.dsh.domain.agent.contract.outbound.InboxNotificationPublisher;
import net.linlan.dsh.domain.agent.model.valobj.InboxSplice;
import net.linlan.dsh.domain.agent.model.valobj.InboxTarget;
import net.linlan.dsh.domain.model.entity.Message;
import net.linlan.dsh.domain.session.event.model.entity.SessionEvent;
import net.linlan.dsh.domain.session.event.model.entity.SessionLog;
import net.linlan.dsh.domain.session.event.model.valobj.InboxSplicedPayload;
import net.linlan.dsh.domain.session.event.service.SessionEventFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Inbox 表示智能体收件箱的领域模型。
 */
public final class Inbox {

    private final SessionLog session;
    private final InboxNotificationPublisher notifications;
    private final List<Message> nextTurn = new ArrayList<>();
    private final List<Message> nextStep = new ArrayList<>();

    /**
     * 构造Inbox领域对象。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param session 会话。
     */
    public Inbox(SessionLog session, InboxNotificationPublisher notifications) {
        this.session = session;
        this.notifications = notifications;
        replay();
    }

    /**
     * 处理replay。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     */
    private void replay() {
        int seedLength = session.header() != null
                ? session.header().seedLength() : 0;
        List<SessionEvent> events = session.events();
        for (int i = seedLength; i < events.size(); i++) {
            SessionEvent event = events.get(i);
            if (!(event instanceof SessionEvent.AgentInboxSpliced spliced)) continue;
            try {
                apply(spliced.data());
            } catch (RuntimeException e) {
                throw new IllegalStateException(
                        "invalid persisted inbox splice at session seq " + event.seq(), e);
            }
        }
    }

    /**
     * 处理nextTurn轮次。
     * @return 领域处理结果。
     */
    public List<Message> nextTurn() {
        return List.copyOf(nextTurn);
    }

    /**
     * 处理nextStep步骤。
     * @return 领域处理结果。
     */
    public List<Message> nextStep() {
        return List.copyOf(nextStep);
    }

    /**
     * 判断是否包含Pending。
     * @return 领域处理结果。
     */
    public boolean hasPending() {
        return !nextTurn.isEmpty() || !nextStep.isEmpty();
    }

    /**
     * 处理clear。
     * @return 领域处理结果。
     */
    public void clear() {
        splice(InboxTarget.NEXT_STEP, 0, nextStep.size(), List.of());
        splice(InboxTarget.NEXT_TURN, 0, nextTurn.size(), List.of());
    }

    /**
     * 处理claim。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param target 目标。
     * @return 领域处理结果。
     */
    public List<Message> claim(InboxTarget target, int turn) {
        List<Message> claimed = new ArrayList<>(mutate(InboxTarget.NEXT_STEP, 0, nextStep.size(), List.of(), false));
        if (target == InboxTarget.NEXT_TURN) {
            claimed.addAll(mutate(InboxTarget.NEXT_TURN, 0, 1, List.of(), false));
        }
        for (Message message : claimed) {
            notifications.claimed(message, turn);
        }
        return claimed;
    }

    /**
     * 处理append。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param target 目标。
     * @return 领域处理结果。
     */
    public void append(InboxTarget target, Message message) {
        splice(target, listFor(target).size(), 0, List.of(message));
    }

    /**
     * 处理prepend。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param target 目标。
     * @return 领域处理结果。
     */
    public void prepend(InboxTarget target, Message message) {
        splice(target, 0, 0, List.of(message));
    }

    /**
     * 处理replace。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param messageId 消息ID。
     * @return 领域处理结果。
     */
    public boolean replace(String messageId, Message newMessage) {
        Optional<Location> location = locate(messageId);
        if (location.isEmpty()) return false;
        Location loc = location.get();
        splice(loc.target(), loc.index(), 1, List.of(newMessage));
        return true;
    }

    /**
     * 处理remove。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param messageId 消息ID。
     * @return 领域处理结果。
     */
    public boolean remove(String messageId) {
        Optional<Location> location = locate(messageId);
        if (location.isEmpty()) return false;
        Location loc = location.get();
        splice(loc.target(), loc.index(), 1, List.of());
        return true;
    }

    /**
     * 处理splice。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param target 目标。
     * @param deleteCount deleteCount。
     * @return 领域处理结果。
     */
    public List<Message> splice(InboxTarget target, int start, int deleteCount, List<Message> inserted) {
        return mutate(target, start, deleteCount, inserted, true);
    }

    // ── Internal ──────────────────────────────────────────────────

    /**
     * 列出For。
     */
    private List<Message> listFor(InboxTarget target) {
        return target == InboxTarget.NEXT_TURN ? nextTurn : nextStep;
    }

    /**
     * 处理locate。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param messageId 消息 ID参数。
     * @return 方法执行结果。
     */
    private Optional<Location> locate(String messageId) {
        for (InboxTarget target : InboxTarget.values()) {
            List<Message> list = listFor(target);
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).id().equals(messageId)) {
                    return Optional.of(new Location(target, i));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * 按 Inbox 目标修改消息队列。
     * <p>流程：定位目标队列 → 应用插入/删除规则 → 返回新的消息列表。</p>
     */
    private List<Message> mutate(
            InboxTarget target,
            int start,
            int deleteCount,
            List<Message> inserted,
            boolean discardRemoved
    ) {
        List<Message> inbox = listFor(target);

        // Normalize start (truncate → clamp, support negative as offset from end)
        int truncatedStart = (int) start; // already int in Java
        int offset = truncatedStart;
        int actualStart = offset < 0
                ? Math.max(inbox.size() + offset, 0)
                : Math.min(offset, inbox.size());

        // Normalize deleteCount
        int truncatedDeleteCount = deleteCount;
        int actualDeleteCount = Math.min(
                Math.max(truncatedDeleteCount, 0),
                inbox.size() - actualStart
        );

        if (actualDeleteCount == 0 && inserted.isEmpty()) {
            return List.of();
        }

        String outcome = (discardRemoved && actualDeleteCount > 0) ? "canceled" : null;
        var splice = new InboxSplice(target, actualStart, actualDeleteCount, inserted, outcome);
        validate(splice);

        // Append durable event BEFORE mutating live state
        var event = SessionEventFactory.inboxSpliced(
                target.wireName(), actualStart, actualDeleteCount, inserted, outcome);
        session.append(event);

        // Mutate live state
        List<Message> removed = new ArrayList<>();
        for (int i = 0; i < actualDeleteCount; i++) {
            removed.add(inbox.remove(actualStart));
        }
        for (int i = inserted.size() - 1; i >= 0; i--) {
            inbox.add(actualStart, inserted.get(i));
        }

        // Publish notifications
        if (discardRemoved) {
            for (Message message : removed) {
                notifications.discarded(message);
            }
        }
        for (Message message : inserted) {
            notifications.inserted(message);
        }

        return removed;
    }

    /**
     * 应用领域规则或状态变更。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param data 数据。
     * @return 领域处理结果。
     */
    private List<Message> apply(InboxSplicedPayload data) {
        InboxTarget target = InboxTarget.fromWireName(data.target());
        int removedCount = data.removedCount();
        List<Message> inserted = data.inserted();

        // Validate
        var splice = new InboxSplice(target, data.start(), removedCount, inserted, data.outcome());
        validate(splice);

        List<Message> inbox = listFor(target);
        List<Message> removed = new ArrayList<>();
        for (int i = 0; i < removedCount; i++) {
            removed.add(inbox.remove(data.start()));
        }
        for (int i = inserted.size() - 1; i >= 0; i--) {
            inbox.add(data.start(), inserted.get(i));
        }
        return removed;
    }

    /**
     * 处理validate。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param splice splice。
     * @return 领域处理结果。
     */
    private void validate(InboxSplice splice) {
        List<Message> inbox = listFor(splice.target());
        int removedCount = splice.removedCount();

        if (splice.start() < 0 || splice.start() > inbox.size()
                || removedCount < 0
                || splice.start() + removedCount > inbox.size()) {
            throw new IllegalStateException("invalid inbox splice: target=" + splice.target()
                    + " start=" + splice.start() + " removedCount=" + removedCount
                    + " listSize=" + inbox.size());
        }

        // Build candidate post-splice list
        List<Message> candidate = new ArrayList<>(inbox);
        for (int i = 0; i < removedCount; i++) {
            candidate.remove(splice.start());
        }
        for (int i = splice.inserted().size() - 1; i >= 0; i--) {
            candidate.add(splice.start(), splice.inserted().get(i));
        }

        // Check for duplicate ids across both lists
        java.util.Set<String> ids = new java.util.HashSet<>();
        List<Message> other = splice.target() == InboxTarget.NEXT_TURN ? nextStep : nextTurn;
        for (Message message : candidate) {
            if (!ids.add(message.id())) {
                throw new IllegalStateException(
                        "message \"" + message.id() + "\" is already pending");
            }
        }
        for (Message message : other) {
            if (!ids.add(message.id())) {
                throw new IllegalStateException(
                        "message \"" + message.id() + "\" is already pending");
            }
        }
    }

    /**
     * Location 定义当前模块中的record契约与职责。
     */
    private record Location(InboxTarget target, int index) {}
}
