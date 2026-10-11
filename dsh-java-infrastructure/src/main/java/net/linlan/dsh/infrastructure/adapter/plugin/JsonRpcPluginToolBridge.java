package net.linlan.dsh.infrastructure.adapter.plugin;

import net.linlan.dsh.domain.tool.plugin.IPluginToolBridge;
import net.linlan.dsh.domain.model.valobj.ContentBlock;
import net.linlan.dsh.domain.model.valobj.TextBlock;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JSON-RPC 2.0 over stdio implementation of {@link IPluginToolBridge}.
 * <p>
 * Communicates with a Node sidecar process using newline-delimited JSON-RPC.
 * The sidecar must implement:
 * <ul>
 *   <li>{@code initialize} — handshake</li>
 *   <li>{@code tools/list} — list tool descriptors</li>
 *   <li>{@code tools/call} — invoke a tool</li>
 * </ul>
 *
 *
 */
public class JsonRpcPluginToolBridge implements IPluginToolBridge {

    private static final Logger log = LoggerFactory.getLogger(JsonRpcPluginToolBridge.class);
    private static final long DEFAULT_TIMEOUT_MS = 60_000;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicLong nextId = new AtomicLong(1);
    private final Map<String, PluginConnection> connections = new ConcurrentHashMap<>();

    @Override
    public CompletableFuture<Void> connect(String pluginId, Process process) {
        return CompletableFuture.runAsync(() -> {
            try {
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

                PluginConnection conn = new PluginConnection(process, writer, reader);
                conn.startReaderThread();
                connections.put(pluginId, conn);

                // Send initialize
                sendRequest(pluginId, "initialize", Map.of(
                        "protocolVersion", "2024-11-05",
                        "clientInfo", Map.of("name", "dsh-java", "version", "1.0.0")
                )).get(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS);

                // Send initialized notification
                sendNotification(pluginId, "notifications/initialized", Map.of());

                log.info("Plugin sidecar connected: {}", pluginId);
            } catch (Exception e) {
                connections.remove(pluginId);
                throw new RuntimeException("Failed to connect plugin sidecar: " + e.getMessage(), e);
            }
        });
    }
    /**
     * disconnect。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param pluginId 插件 ID。
     * @return 方法执行结果。
     */


    @Override
    public CompletableFuture<Void> disconnect(String pluginId) {
        return CompletableFuture.runAsync(() -> {
            PluginConnection conn = connections.remove(pluginId);
            if (conn != null) {
                conn.dispose();
                log.info("Plugin sidecar disconnected: {}", pluginId);
            }
        });
    }
    /**
     * 判断当前状态是否满足条件。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param pluginId 插件 ID。
     * @return 布尔结果：满足条件为 true，否则 false。
     */


    @Override
    public boolean isConnected(String pluginId) {
        PluginConnection conn = connections.get(pluginId);
        return conn != null && conn.isConnected();
    }
    /**
     * 列出Tools。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param pluginId 插件 ID。
     * @return 方法执行结果。
     */


    @Override
    public CompletableFuture<List<PluginToolDescriptor>> listTools(String pluginId) {
        return sendRequest(pluginId, "tools/list", Map.of())
                .thenApply(result -> {
                    List<PluginToolDescriptor> tools = new ArrayList<>();
                    JsonNode toolsNode = result.path("tools");
                    if (toolsNode.isArray()) {
                        for (JsonNode toolNode : toolsNode) {
                            String name = toolNode.path("name").asText();
                            String description = toolNode.path("description").asText("");
                            Map<String, Object> inputSchema = objectMapper.convertValue(
                                    toolNode.path("inputSchema"), Map.class);
                            tools.add(new PluginToolDescriptor(name, description, inputSchema));
                        }
                    }
                    return tools;
                });
    }
    /**
     * call Tool。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param pluginId 插件 ID。
     * @param toolName tool Name参数。
     * @param Map<String Map String参数。
     * @param arguments arguments参数。
     * @return 方法执行结果。
     */


    @Override
    public CompletableFuture<PluginToolResult> callTool(String pluginId, String toolName, Map<String, Object> arguments) {
        Map<String, Object> params = new HashMap<>();
        params.put("name", toolName);
        params.put("arguments", arguments);

        return sendRequest(pluginId, "tools/call", params)
                .thenApply(result -> {
                    List<ContentBlock> content = new ArrayList<>();
                    JsonNode contentNode = result.path("content");
                    if (contentNode.isArray()) {
                        for (JsonNode item : contentNode) {
                            String type = item.path("type").asText();
                            if ("text".equals(type)) {
                                content.add(new TextBlock(item.path("text").asText()));
                            }
                        }
                    }
                    boolean isError = result.path("isError").asBoolean(false);
                    String errorText = isError ? content.stream()
                            .map(b -> b instanceof TextBlock t ? t.text() : "")
                            .reduce("", (a, b) -> a + b) : null;
                    return new PluginToolResult(content, isError, errorText);
                });
    }

    // ── JSON-RPC transport ───────────────────────────────────────

    /**
     * 发送数据或请求到目标端。
     */
    private CompletableFuture<JsonNode> sendRequest(String pluginId, String method, Map<String, Object> params) {
        PluginConnection conn = connections.get(pluginId);
        if (conn == null || !conn.isConnected()) {
            return CompletableFuture.failedFuture(new RuntimeException("Plugin not connected: " + pluginId));
        }

        long id = nextId.getAndIncrement();
        ObjectNode request = objectMapper.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", id);
        request.put("method", method);
        request.set("params", objectMapper.valueToTree(params));

        CompletableFuture<JsonNode> future = new CompletableFuture<>();
        conn.pending.put(id, future);

        try {
            String json = objectMapper.writeValueAsString(request);
            synchronized (conn.writer) {
                conn.writer.write(json);
                conn.writer.newLine();
                conn.writer.flush();
            }
        } catch (IOException e) {
            conn.pending.remove(id);
            future.completeExceptionally(new RuntimeException("Failed to send: " + e.getMessage(), e));
        }

        return future.orTimeout(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    /**
     * 发送数据或请求到目标端。
     */
    private void sendNotification(String pluginId, String method, Map<String, Object> params) {
        PluginConnection conn = connections.get(pluginId);
        if (conn == null) return;

        try {
            ObjectNode notification = objectMapper.createObjectNode();
            notification.put("jsonrpc", "2.0");
            notification.put("method", method);
            notification.set("params", objectMapper.valueToTree(params));

            String json = objectMapper.writeValueAsString(notification);
            synchronized (conn.writer) {
                conn.writer.write(json);
                conn.writer.newLine();
                conn.writer.flush();
            }
        } catch (IOException e) {
            log.warn("Failed to send notification to plugin {}: {}", pluginId, e.getMessage());
        }
    }

    // ── Connection state ─────────────────────────────────────────

    /**
     * PluginConnection 是基础设施适配器，负责外部资源与领域端口对接。
     */
    private class PluginConnection {
        private final Process process;
        private final BufferedWriter writer;
        private final BufferedReader reader;
        private final Map<Long, CompletableFuture<JsonNode>> pending = new ConcurrentHashMap<>();
        private Thread readerThread;
        private volatile boolean connected = false;

        PluginConnection(Process process, BufferedWriter writer, BufferedReader reader) {
            this.process = process;
            this.writer = writer;
            this.reader = reader;
        }

        boolean isConnected() {
            return connected && process.isAlive();
        }

        void startReaderThread() {
            connected = true;
            readerThread = new Thread(this::readLoop, "plugin-sidecar-reader-" + pluginId());
            readerThread.setDaemon(true);
            readerThread.start();
        }

        /**
         * 处理插件 ID。
         * @return 方法执行结果。
         */
        private String pluginId() {
            // Best-effort identification for thread name
            return String.valueOf(process.pid());
        }

        /**
         * 读取 Loop。
         * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
         */
        private void readLoop() {
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) continue;
                    try {
                        JsonNode message = objectMapper.readTree(line);
                        if (message.has("id")) {
                            long id = message.get("id").asLong();
                            CompletableFuture<JsonNode> future = pending.remove(id);
                            if (future != null) {
                                if (message.has("error")) {
                                    JsonNode error = message.get("error");
                                    future.completeExceptionally(new RuntimeException(
                                            "JSON-RPC error " + error.path("code").asInt() + ": "
                                                    + error.path("message").asText()));
                                } else {
                                    future.complete(message.path("result"));
                                }
                            }
                        }
                    } catch (Exception e) {
                        log.debug("Skipping malformed line from sidecar: {}", e.getMessage());
                    }
                }
            } catch (IOException e) {
                // Stream closed — normal during disconnect
            } finally {
                connected = false;
                pending.values().forEach(f -> f.completeExceptionally(new RuntimeException("Connection closed")));
                pending.clear();
            }
        }

        void dispose() {
            connected = false;
            try {
                if (process.isAlive()) {
                    process.destroy();
                    if (!process.waitFor(5, TimeUnit.SECONDS)) {
                        process.destroyForcibly();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            pending.values().forEach(f -> f.completeExceptionally(new RuntimeException("Disposed")));
            pending.clear();
        }
    }
}
