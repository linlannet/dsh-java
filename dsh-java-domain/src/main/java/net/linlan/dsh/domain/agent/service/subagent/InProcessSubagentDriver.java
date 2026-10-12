package net.linlan.dsh.domain.agent.service.subagent;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * InProcessSubagentDriver 进程内子代理驱动，管理子代理生命周期 spawn/send/interrupt/shutdown。
 */
public final class InProcessSubagentDriver {

    public record SubagentSpec(String agentPreset, String cwd, String initialMessage) {

        public SubagentSpec {
            Objects.requireNonNull(agentPreset, "agentPreset must not be null");
            Objects.requireNonNull(cwd, "cwd must not be null");
        }
    }

    private final Map<String, SubagentHandle> handles = new ConcurrentHashMap<>();
    private final AtomicInteger idCounter = new AtomicInteger(0);

    public String spawn(SubagentSpec spec) {
        Objects.requireNonNull(spec, "spec must not be null");
        String id = "subagent-" + idCounter.incrementAndGet();
        SubagentHandle handle = new SubagentHandle(id, spec);
        handles.put(id, handle);
        if (spec.initialMessage() != null && !spec.initialMessage().isEmpty()) {
            handle.inbox.add(spec.initialMessage());
        }
        return id;
    }

    public void send(String subagentId, String message) {
        SubagentHandle handle = requireHandle(subagentId);
        if (handle.shutdown) {
            throw new IllegalStateException("subagent already shutdown: " + subagentId);
        }
        Objects.requireNonNull(message, "message must not be null");
        handle.inbox.add(message);
    }

    public void interrupt(String subagentId) {
        SubagentHandle handle = requireHandle(subagentId);
        handle.interrupted = true;
    }

    public void shutdown(String subagentId) {
        SubagentHandle handle = requireHandle(subagentId);
        handle.shutdown = true;
        handle.inbox.clear();
    }

    public boolean isAlive(String subagentId) {
        SubagentHandle handle = handles.get(subagentId);
        return handle != null && !handle.shutdown;
    }

    public boolean isInterrupted(String subagentId) {
        SubagentHandle handle = handles.get(subagentId);
        return handle != null && handle.interrupted;
    }

    public SubagentSpec specOf(String subagentId) {
        SubagentHandle handle = requireHandle(subagentId);
        return handle.spec;
    }

    public int pendingMessageCount(String subagentId) {
        SubagentHandle handle = requireHandle(subagentId);
        return handle.inbox.size();
    }

    public String pollMessage(String subagentId) {
        SubagentHandle handle = requireHandle(subagentId);
        return handle.inbox.poll();
    }

    private SubagentHandle requireHandle(String subagentId) {
        SubagentHandle handle = handles.get(subagentId);
        if (handle == null) {
            throw new IllegalArgumentException("unknown subagentId: " + subagentId);
        }
        return handle;
    }

    private static final class SubagentHandle {
        final String id;
        final SubagentSpec spec;
        final LinkedBlockingQueue<String> inbox = new LinkedBlockingQueue<>();
        volatile boolean interrupted = false;
        volatile boolean shutdown = false;

        SubagentHandle(String id, SubagentSpec spec) {
            this.id = id;
            this.spec = spec;
        }
    }
}
