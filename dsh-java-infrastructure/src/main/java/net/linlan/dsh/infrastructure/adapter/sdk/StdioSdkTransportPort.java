package net.linlan.dsh.infrastructure.adapter.sdk;

import net.linlan.dsh.domain.sdk.adapter.port.ISdkTransportPort;
import net.linlan.dsh.domain.sdk.service.SdkStandaloneCondition;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Service;

/**
 * Newline-delimited JSON-RPC 2.0 transport over stdin/stdout.
 *
 * <p>Frames with id and method are requests, id alone is a response, and
 * method alone is a notification. Malformed lines are ignored.
 *
 *
 */
@Service
@Conditional(SdkStandaloneCondition.class)
public class StdioSdkTransportPort implements ISdkTransportPort {

    private final ObjectMapper mapper;
    private final BufferedReader reader;
    private final BufferedWriter writer;
    private final Map<String, CompletableFuture<Object>> pending = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong(1);
    private final List<Consumer<NotificationFrame>> notificationHandlers = new CopyOnWriteArrayList<>();
    private final Map<String, Function<Object, Object>> requestHandlers = new ConcurrentHashMap<>();
    private final ExecutorService readLoop = Executors.newSingleThreadExecutor();
    private volatile boolean closed = false;

    /**
     * 处理Stdio Sdk Transport Port。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param in in参数。
     * @param out out参数。
     * @param mapper mapper参数。
     * @return 方法执行结果。
     */
    public StdioSdkTransportPort(InputStream in, OutputStream out, ObjectMapper mapper) {
        this.mapper = mapper;
        this.reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        this.writer = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
        readLoop.submit(this::readLoop);
    }

    @Override
    public Object request(String method, Object params) {
        String id = String.valueOf(nextId.getAndIncrement());
        CompletableFuture<Object> future = new CompletableFuture<>();
        pending.put(id, future);

        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put("jsonrpc", "2.0");
        frame.put("id", id);
        frame.put("method", method);
        if (params != null) frame.put("params", params);

        try {
            writeFrame(frame);
        } catch (Exception e) {
            pending.remove(id);
            throw new RuntimeException("failed to send request: " + e.getMessage(), e);
        }

        try {
            return future.get(60, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            pending.remove(id);
            throw new RuntimeException("request timed out: " + method, e);
        } catch (Exception e) {
            throw new RuntimeException("request failed: " + e.getMessage(), e);
        }
    }
    /**
     * notify。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param method method参数。
     * @param params params参数。
     */


    @Override
    public void notify(String method, Object params) {
        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put("jsonrpc", "2.0");
        frame.put("method", method);
        if (params != null) frame.put("params", params);
        try {
            writeFrame(frame);
        } catch (Exception e) {
            throw new RuntimeException("failed to send notification: " + e.getMessage(), e);
        }
    }
    /**
     * on Notification。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param handler handler参数。
     */


    @Override
    public void onNotification(Consumer<NotificationFrame> handler) {
        notificationHandlers.add(handler);
    }

    /**
     * 注册 JSON-RPC 请求处理函数。
     *
     * @param method  JSON-RPC 方法名
     * @param handler 请求处理函数（params → result）
     */
    public void registerRequestHandler(String method, Function<Object, Object> handler) {
        requestHandlers.put(method, handler);
    }
    /**
     * 关闭目标对象。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     */


    @Override
    public void close() {
        closed = true;
        readLoop.shutdownNow();
        failPending(new IOException("transport closed"));
    }

    /**
     * 读取 Loop。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     */
    private void readLoop() {
        while (!closed) {
            try {
                String line = reader.readLine();
                if (line == null) {
                    failPending(new IOException("JSON-RPC input closed"));
                    break;
                }
                line = line.trim();
                if (line.isEmpty()) continue;
                handleLine(line);
            } catch (IOException e) {
                if (!closed) failPending(e);
                break;
            }
        }
    }

    /**
     * 处理一行 JSON-RPC 帧。
     * <p>流程：解析 JSON → 区分请求/响应/通知 → 分发到对应回调。</p>
     *
     * @param line 一行 JSON 文本。
     */
    @SuppressWarnings("unchecked")
    private void handleLine(String line) {
        Map<String, Object> frame;
        try {
            frame = mapper.readValue(line, Map.class);
        } catch (Exception e) {
            return;
        }

        Object id = frame.get("id");
        String method = (String) frame.get("method");

        if (id != null && method != null) {
            // Incoming request — dispatch to registered handler
            Function<Object, Object> handler = requestHandlers.get(method);
            if (handler != null) {
                Object result;
                try {
                    result = handler.apply(frame.get("params"));
                } catch (Exception e) {
                    Map<String, Object> errorFrame = new LinkedHashMap<>();
                    errorFrame.put("jsonrpc", "2.0");
                    errorFrame.put("id", id);
                    errorFrame.put("error", java.util.Map.of("code", -32603, "message", e.getMessage()));
                    try { writeFrame(errorFrame); } catch (Exception ignored) {}
                    return;
                }
                Map<String, Object> responseFrame = new LinkedHashMap<>();
                responseFrame.put("jsonrpc", "2.0");
                responseFrame.put("id", id);
                responseFrame.put("result", result);
                try { writeFrame(responseFrame); } catch (Exception ignored) {}
            }
            return;
        }
        if (id != null) {
            // Response to our request
            CompletableFuture<Object> future = pending.remove(String.valueOf(id));
            if (future == null) return;
            if (frame.containsKey("error")) {
                future.completeExceptionally(
                        new RuntimeException("JSON-RPC error: " + frame.get("error")));
            } else {
                future.complete(frame.get("result"));
            }
            return;
        }
        if (method != null) {
            // Notification
            NotificationFrame nf = new NotificationFrame(method, frame.get("params"));
            for (Consumer<NotificationFrame> handler : notificationHandlers) {
                try { handler.accept(nf); } catch (Exception ignored) {}
            }
        }
    }

    /**
     * 写入 Frame。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param Map<String 映射 String参数。
     * @param frame frame参数。
     */
    private void writeFrame(Map<String, Object> frame) throws IOException {
        synchronized (writer) {
            writer.write(mapper.writeValueAsString(frame));
            writer.write("\n");
            writer.flush();
        }
    }

    /**
     * 处理fail Pending。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param e 异常对象。
     */
    private void failPending(Exception e) {
        for (CompletableFuture<Object> f : pending.values()) {
            f.completeExceptionally(e);
        }
        pending.clear();
    }
}
