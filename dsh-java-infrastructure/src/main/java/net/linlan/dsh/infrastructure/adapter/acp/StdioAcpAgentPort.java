package net.linlan.dsh.infrastructure.adapter.acp;

import net.linlan.dsh.domain.acp.adapter.port.IAcpAgentPort;
import net.linlan.dsh.domain.acp.model.valobj.AcpMessage;
import net.linlan.dsh.domain.acp.model.valobj.AcpPromptRequest;
import net.linlan.dsh.domain.acp.model.valobj.AcpPromptResponse;
import net.linlan.dsh.domain.acp.model.valobj.AcpSessionInfo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ACP agent port over JSON-RPC stdio. Spawns a child ACP agent process and
 * communicates via newline-delimited JSON.
 *
 * <p>调用流程：领域端口 → 基础设施适配器 → 外部系统。</p>
 * <p>示例：适配器只实现端口契约，业务规则保留在 domain。</p>
 *
 */
public class StdioAcpAgentPort implements IAcpAgentPort {

    private static final String PROTOCOL_VERSION = "2025-03-26";
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, AcpConnection> connections = new ConcurrentHashMap<>();
    private final AtomicLong requestId = new AtomicLong(0);
    private final String command;
    private final List<String> args;

    /**
     * 处理Stdio ACP Agent Port。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param command 命令对象。
     * @param args 启动参数。
     * @return 方法执行结果。
     */
    public StdioAcpAgentPort(String command, List<String> args) {
        this.command = command;
        this.args = args;
    }

    @Override
    public AcpSessionInfo initialize(String cwd, String mcpConfig) {
        try {
            List<String> cmd = new ArrayList<>();
            cmd.add(command);
            cmd.addAll(args);
            ProcessBuilder pb = new ProcessBuilder(cmd);
            if (cwd != null) pb.directory(new File(cwd));
            pb.redirectErrorStream(false);
            Process process = pb.start();

            String sessionId = "acp_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            AcpConnection conn = new AcpConnection(process, sessionId);
            connections.put(sessionId, conn);

            // Send initialize request
            ObjectNode initReq = mapper.createObjectNode();
            initReq.put("jsonrpc", "2.0");
            initReq.put("id", requestId.incrementAndGet());
            initReq.put("method", "initialize");
            ObjectNode params = mapper.createObjectNode();
            params.put("protocolVersion", PROTOCOL_VERSION);
            params.put("clientName", "dsh-java");
            params.put("clientVersion", "1.0.0");
            initReq.set("params", params);
            conn.send(initReq.toString());
            conn.readResponse(); // wait for initialize response

            // Send new session request
            ObjectNode newSessionReq = mapper.createObjectNode();
            newSessionReq.put("jsonrpc", "2.0");
            newSessionReq.put("id", requestId.incrementAndGet());
            newSessionReq.put("method", "session/new");
            ObjectNode sessionParams = mapper.createObjectNode();
            if (cwd != null) sessionParams.put("cwd", cwd);
            if (mcpConfig != null) sessionParams.put("mcpConfig", mapper.readTree(mcpConfig));
            newSessionReq.set("params", sessionParams);
            conn.send(newSessionReq.toString());
            JsonNode resp = conn.readResponse();
            String sid = resp.path("result").path("sessionId").asText(sessionId);

            return new AcpSessionInfo(sid, cwd, mcpConfig);
        } catch (Exception e) {
            throw new RuntimeException("ACP initialize failed: " + e.getMessage(), e);
        }
    }

    /**
     * 发送提示词并等待结果。
     * @param request 请求对象。
     * @return 方法执行结果。
     */
    @Override
    public AcpPromptResponse prompt(AcpPromptRequest request) {
        AcpConnection conn = connections.get(request.sessionId());
        if (conn == null) throw new IllegalArgumentException("unknown session: " + request.sessionId());
        try {
            ObjectNode promptReq = mapper.createObjectNode();
            promptReq.put("jsonrpc", "2.0");
            promptReq.put("id", requestId.incrementAndGet());
            promptReq.put("method", "session/prompt");
            ObjectNode params = mapper.createObjectNode();
            ArrayNode messages = mapper.createArrayNode();
            for (AcpMessage msg : request.messages()) {
                ObjectNode msgNode = mapper.createObjectNode();
                msgNode.put("role", msg.role());
                ArrayNode content = mapper.createArrayNode();
                for (AcpMessage.AcpContentBlock block : msg.content()) {
                    if (block instanceof AcpMessage.AcpTextBlock tb) {
                        ObjectNode textBlock = mapper.createObjectNode();
                        textBlock.put("type", "text");
                        textBlock.put("text", tb.text());
                        content.add(textBlock);
                    } else if (block instanceof AcpMessage.AcpImageBlock ib) {
                        ObjectNode imgBlock = mapper.createObjectNode();
                        imgBlock.put("type", "image");
                        imgBlock.put("mimeType", ib.mediaType());
                        imgBlock.put("data", ib.base64Data());
                        content.add(imgBlock);
                    }
                }
                msgNode.set("content", content);
                messages.add(msgNode);
            }
            params.set("messages", messages);
            promptReq.set("params", params);
            conn.send(promptReq.toString());
            JsonNode resp = conn.readResponse();
            String stopReason = resp.path("result").path("stopReason").asText("end_turn");
            return new AcpPromptResponse(request.sessionId(), stopReason, List.of());
        } catch (Exception e) {
            return new AcpPromptResponse(request.sessionId(), "error", List.of());
        }
    }

    /**
     * 取消目标对象。
     * @param sessionId 会话 ID。
     */
    @Override
    public void cancel(String sessionId) {
        AcpConnection conn = connections.get(sessionId);
        if (conn == null) return;
        try {
            ObjectNode cancelNotif = mapper.createObjectNode();
            cancelNotif.put("jsonrpc", "2.0");
            cancelNotif.put("method", "session/cancel");
            ObjectNode params = mapper.createObjectNode();
            params.put("sessionId", sessionId);
            cancelNotif.set("params", params);
            conn.send(cancelNotif.toString());
        } catch (Exception ignored) {}
    }

    /**
     * 释放资源。
     * @param sessionId 会话 ID。
     */
    @Override
    public void dispose(String sessionId) {
        AcpConnection conn = connections.remove(sessionId);
        if (conn != null) conn.close();
    }

    /**
     * AcpConnection 是基础设施适配器，负责外部资源与领域端口对接。
     */
    private class AcpConnection {
        private final Process process;
        private final BufferedWriter writer;
        private final BufferedReader reader;
        private final String sessionId;

        AcpConnection(Process process, String sessionId) {
            this.process = process;
            this.sessionId = sessionId;
            this.writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
            this.reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        }

        void send(String json) throws IOException {
            writer.write(json);
            writer.write("\n");
            writer.flush();
        }

        JsonNode readResponse() throws IOException {
            String line = reader.readLine();
            if (line == null) throw new IOException("ACP process closed stdout");
            return mapper.readTree(line);
        }

        void close() {
            try { process.destroy(); } catch (Exception ignored) {}
        }
    }
}
