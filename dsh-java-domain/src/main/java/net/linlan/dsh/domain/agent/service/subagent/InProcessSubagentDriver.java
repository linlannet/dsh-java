package net.linlan.dsh.domain.agent.service.subagent;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * InProcessSubagentDriver 进程内子代理驱动，管理子代理生命周期 spawn/send/interrupt/shutdown。
 */
public final class InProcessSubagentDriver {

    /**
     * 子代理创建规格。
     *
     * @param agentPreset   代理预设
     * @param initialMessage 初始消息
     * @param name          可选名称；为空时使用生成的会话 id
     * @param description   可选描述，用于 list_agents
     */
    public record SubagentSpec(String agentPreset, String initialMessage,
                               String name, String description) {

        public SubagentSpec {
            Objects.requireNonNull(agentPreset, "agentPreset must not be null");
        }

        public SubagentSpec(String agentPreset, String initialMessage) {
            this(agentPreset, initialMessage, null, null);
        }
    }

    /**
     * 子代理列表项。
     *
     * @param id          会话 id
     * @param name        名称
     * @param alive       是否存活
     * @param description 描述
     */
    public record AgentInfo(String id, String name, boolean alive, String description) {
    }

    private static final int DEFAULT_CAPACITY_LIMIT = 16;

    private final Map<String, SubagentHandle> handles = new ConcurrentHashMap<>();
    private final Map<String, String> nameToId = new ConcurrentHashMap<>();
    private final AtomicInteger idCounter = new AtomicInteger(0);
    private final int capacityLimit;

    public InProcessSubagentDriver() {
        this(DEFAULT_CAPACITY_LIMIT);
    }

    /**
     * 以指定共享容量上限创建驱动。
     *
     * @param capacityLimit 容量上限，必须为正数
     */
    public InProcessSubagentDriver(int capacityLimit) {
        if (capacityLimit <= 0) {
            throw new IllegalArgumentException("capacityLimit must be positive");
        }
        this.capacityLimit = capacityLimit;
    }

    /**
     * 返回共享容量上限。
     *
     * @return 上限值
     */
    public int capacityLimit() {
        return capacityLimit;
    }

    public String spawn(SubagentSpec spec) {
        Objects.requireNonNull(spec, "spec must not be null");
        if (handles.size() >= capacityLimit) {
            throw new IllegalStateException("subagent capacity exceeded: limit=" + capacityLimit);
        }
        String id = "subagent-" + idCounter.incrementAndGet();
        String name = spec.name() == null || spec.name().isBlank() ? id : spec.name().trim();
        if (nameToId.putIfAbsent(name, id) != null) {
            throw new IllegalArgumentException("subagent name already in use: " + name);
        }
        SubagentHandle handle = new SubagentHandle(id, name, spec);
        handles.put(id, handle);
        if (spec.initialMessage() != null && !spec.initialMessage().isEmpty()) {
            handle.inbox.add(spec.initialMessage());
        }
        return id;
    }

    /**
     * 将名称或会话 id 解析为会话 id。
     *
     * @param agent 名称或会话 id
     * @return 会话 id
     */
    public String resolveAgent(String agent) {
        if (agent == null || agent.isBlank()) {
            throw new IllegalArgumentException("agent required");
        }
        if (handles.containsKey(agent)) {
            return agent;
        }
        String id = nameToId.get(agent);
        if (id == null) {
            throw new IllegalArgumentException("unknown subagent: " + agent);
        }
        return id;
    }

    /**
     * 查询子代理信息。
     *
     * @param subagentId 会话 id
     * @return 信息；不存在时返回 null
     */
    public AgentInfo info(String subagentId) {
        SubagentHandle handle = handles.get(subagentId);
        if (handle == null) {
            return null;
        }
        return new AgentInfo(handle.id, handle.name, !handle.shutdown, handle.spec.description());
    }

    /**
     * 列出全部子代理（含已完成）。
     *
     * @return 子代理列表
     */
    public List<AgentInfo> listAgents() {
        return handles.values().stream()
                .map(handle -> new AgentInfo(handle.id, handle.name, !handle.shutdown,
                        handle.spec.description()))
                .toList();
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
        final String name;
        final SubagentSpec spec;
        final LinkedBlockingQueue<String> inbox = new LinkedBlockingQueue<>();
        volatile boolean interrupted = false;
        volatile boolean shutdown = false;

        SubagentHandle(String id, String name, SubagentSpec spec) {
            this.id = id;
            this.name = name;
            this.spec = spec;
        }
    }
}
