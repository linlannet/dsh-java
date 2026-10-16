package net.linlan.dsh.domain.api.gateway.service;

import net.linlan.dsh.domain.api.gateway.model.valobj.GatewayStreamItem;
import net.linlan.dsh.domain.api.gateway.model.valobj.InvokeRemoteRequest;
import net.linlan.dsh.domain.api.gateway.model.valobj.PeerScope;
import net.linlan.dsh.domain.typert.ITypertService;
import net.linlan.dsh.domain.typert.model.entity.InvocationDescriptor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Service;

/**
 * TypertGatewayService 依据生成的 InvocationDescriptor 约定分派 Client 调用、校验参数、
 * 支持取消与重连，转发 Host 事件，并在同一条逻辑流上承载多路复用的 Remote 流。
 *
 * <p>调用流程：解析 InvocationDescriptor → 校验参数 → 判定访问权限 → 分派至 Host → 转发事件。</p>
 * <p>多路复用：按 Peer 连接 ID 维护流订阅状态，重连时按序列号恢复并重发未确认流项。</p>
 *
 */
@Service
public class TypertGatewayService {

    private final ITypertService typertService;
    private final Map<String, PeerStreamState> peerStreams = new ConcurrentHashMap<>();
    private final AtomicLong globalSequence = new AtomicLong(0);

    /**
     * 构造 TypertGatewayService。
     * @param typertService Typert 服务，用于解析 InvocationDescriptor
     */
    public TypertGatewayService(ITypertService typertService) {
        this.typertService = typertService;
    }

    /**
     * 分派一次 Remote 调用至 Host。
     * 流程：解析 InvocationDescriptor → 校验参数 → 创建流上下文 → 返回流句柄。
     * @param request Remote 调用请求
     * @return 流句柄，用于后续订阅事件与取消
     */
    public GatewayStreamHandle dispatch(InvokeRemoteRequest request) {
        Objects.requireNonNull(request, "request 不能为空");

        Optional<InvocationDescriptor> descriptorOpt = typertService.resolveInvocation(request.endpoint());
        if (descriptorOpt.isEmpty()) {
            throw new GatewayDispatchException("未知 endpoint: " + request.endpoint());
        }

        InvocationDescriptor descriptor = descriptorOpt.get();
        validateParams(descriptor, request.args());

        String streamId = request.peer().connectionId() + ":" + globalSequence.incrementAndGet();
        PeerStreamState state = peerStreams.computeIfAbsent(request.peer().connectionId(), k -> new PeerStreamState());
        StreamContext ctx = new StreamContext(streamId, descriptor, request);
        state.addStream(ctx);

        return new GatewayStreamHandle(streamId, ctx);
    }

    /**
     * 校验参数是否符合 InvocationDescriptor 的 paramsSchema。
     * @param descriptor 调用描述符
     * @param args 调用参数
     */
    private void validateParams(InvocationDescriptor descriptor, Map<String, Object> args) {
        Map<String, Object> schema = descriptor.paramsSchema();
        if (schema == null || schema.isEmpty()) {
            return;
        }
        for (String key : schema.keySet()) {
            if (!args.containsKey(key) && isRequired(schema, key)) {
                throw new GatewayDispatchException("缺少必需参数: " + key);
            }
        }
    }

    private boolean isRequired(Map<String, Object> schema, String key) {
        Object fieldSchema = schema.get(key);
        if (fieldSchema instanceof Map<?, ?> m) {
            Object required = m.get("required");
            return Boolean.TRUE.equals(required);
        }
        return false;
    }

    /**
     * 订阅多路复用流，在同一条逻辑流上承载多路复用的 Remote 流。
     * 按 Peer 连接 ID 维护流订阅状态，重连时按 ID 恢复并重发未确认流项。
     * @param peer Peer 作用域
     * @param lastAckSequence 客户端最后确认的序列号（重连时传入，首次订阅传 -1）
     * @return 从 lastAckSequence+1 开始的未确认流项列表
     */
    public List<GatewayStreamItem> subscribeMultiplexed(PeerScope peer, long lastAckSequence) {
        Objects.requireNonNull(peer, "peer 不能为空");

        PeerStreamState state = peerStreams.get(peer.connectionId());
        if (state == null) {
            return List.of();
        }

        return state.itemsAfter(lastAckSequence);
    }

    /**
     * 确认流项已接收，标记为已确认。
     * @param connectionId Peer 连接 ID
     * @param sequence 流项序列号
     */
    public void acknowledge(String connectionId, long sequence) {
        PeerStreamState state = peerStreams.get(connectionId);
        if (state != null) {
            state.acknowledge(sequence);
        }
    }

    /**
     * 取消指定流。
     * @param streamId 流 ID
     */
    public void cancel(String streamId) {
        for (PeerStreamState state : peerStreams.values()) {
            state.removeStream(streamId);
        }
    }

    /**
     * Peer 断连时清理其所有流状态。重连时由客户端传入 lastAckSequence 恢复。
     * @param connectionId Peer 连接 ID
     */
    public void disposePeer(String connectionId) {
        PeerStreamState state = peerStreams.get(connectionId);
        if (state != null) {
            state.markDisconnected();
        }
    }

    /**
     * 向指定 Peer 的流发送一个事件项。
     * @param connectionId Peer 连接 ID
     * @param payload 事件负载
     * @return 生成的流项
     */
    public GatewayStreamItem emit(String connectionId, Object payload) {
        PeerStreamState state = peerStreams.computeIfAbsent(connectionId, k -> new PeerStreamState());
        return state.emit(payload, globalSequence.incrementAndGet());
    }

    public static class GatewayStreamHandle {
        private final String streamId;
        private final StreamContext context;

        GatewayStreamHandle(String streamId, StreamContext context) {
            this.streamId = streamId;
            this.context = context;
        }

        public String streamId() { return streamId; }
        public InvocationDescriptor descriptor() { return context.descriptor; }
        public InvokeRemoteRequest request() { return context.request; }
    }

    static class StreamContext {
        final String streamId;
        final InvocationDescriptor descriptor;
        final InvokeRemoteRequest request;
        volatile boolean cancelled = false;

        StreamContext(String streamId, InvocationDescriptor descriptor, InvokeRemoteRequest request) {
            this.streamId = streamId;
            this.descriptor = descriptor;
            this.request = request;
        }
    }

    static class PeerStreamState {
        private final ConcurrentLinkedQueue<GatewayStreamItem> items = new ConcurrentLinkedQueue<>();
        private final Map<String, StreamContext> streams = new ConcurrentHashMap<>();
        private final AtomicReference<String> status = new AtomicReference<>("connected");

        void addStream(StreamContext ctx) {
            streams.put(ctx.streamId, ctx);
        }

        void removeStream(String streamId) {
            StreamContext ctx = streams.remove(streamId);
            if (ctx != null) {
                ctx.cancelled = true;
            }
        }

        GatewayStreamItem emit(Object payload, long sequence) {
            GatewayStreamItem item = GatewayStreamItem.of(sequence, payload);
            items.add(item);
            return item;
        }

        List<GatewayStreamItem> itemsAfter(long lastAckSequence) {
            List<GatewayStreamItem> result = new ArrayList<>();
            for (GatewayStreamItem item : items) {
                if (item.sequence() > lastAckSequence && !item.acknowledged()) {
                    result.add(item);
                }
            }
            return result;
        }

        void acknowledge(long sequence) {
            for (GatewayStreamItem item : items) {
                if (item.sequence() == sequence) {
                    items.remove(item);
                    items.add(item.ack());
                    break;
                }
            }
        }

        void markDisconnected() {
            status.set("disconnected");
        }

        boolean isConnected() {
            return "connected".equals(status.get());
        }
    }

    public static class GatewayDispatchException extends RuntimeException {
        public GatewayDispatchException(String message) {
            super(message);
        }
    }
}
