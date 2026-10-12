package net.linlan.dsh.infrastructure.adapter.mcp;

import net.linlan.dsh.domain.tool.mcp.resource.adapter.port.IMcpResourcePort;
import net.linlan.dsh.domain.tool.mcp.resource.model.valobj.McpResource;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * StdioMcpResourcePort 表示通过 stdio JSON-RPC 实现 MCP 资源端口的基础设施适配器。
 *
 * <p>调用流程：领域端口 → 基础设施适配器 → 外部系统。</p>
 * <p>示例：适配器只实现端口契约，业务规则保留在 domain。</p>
 *
 */
public class StdioMcpResourcePort implements IMcpResourcePort {

    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicLong nextId = new AtomicLong(1);
    private final String command;
    private final List<String> args;
    private Process process;
    private BufferedWriter writer;
    private BufferedReader reader;

    /**
     * 处理Stdio Mcp Resource Port。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param command command参数。
     * @param args args参数。
     * @return 方法执行结果。
     */
    public StdioMcpResourcePort(String command, List<String> args) {
        this.command = command;
        this.args = args != null ? List.copyOf(args) : List.of();
    }

    @Override
    public List<McpResource> listResources(String scope) {
        ensureConnected();
        try {
            ObjectNode params = mapper.createObjectNode();
            if (scope != null && !scope.isBlank()) {
                params.put("scope", scope);
            }
            JsonNode result = call("resources/list", params);
            List<McpResource> resources = new ArrayList<>();
            JsonNode arr = result.path("resources");
            if (arr.isArray()) {
                for (JsonNode node : arr) {
                    resources.add(new McpResource(
                            node.path("uri").asText(""),
                            node.path("name").asText(""),
                            node.path("description").asText(""),
                            node.path("scope").asText(scope != null ? scope : "")
                    ));
                }
            }
            return resources;
        } catch (Exception e) {
            return List.of();
        }
    }

    @Override
    public String readResource(String uri) {
        ensureConnected();
        try {
            ObjectNode params = mapper.createObjectNode();
            params.put("uri", uri);
            JsonNode result = call("resources/read", params);
            JsonNode contents = result.path("contents");
            if (contents.isArray() && !contents.isEmpty()) {
                return contents.get(0).path("text").asText("");
            }
            return "";
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 处理connect。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @return 方法执行结果。
     */
    public void connect() throws IOException {
        List<String> cmd = new ArrayList<>();
        cmd.add(command);
        cmd.addAll(args);
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(false);
        process = pb.start();
        writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
        reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
    }

    /**
     * 处理disconnect。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @return 方法执行结果。
     */
    public void disconnect() {
        if (process != null && process.isAlive()) {
            process.destroy();
        }
        process = null;
        writer = null;
        reader = null;
    }

    private void ensureConnected() {
        if (process == null || !process.isAlive()) {
            throw new IllegalStateException("MCP resource port not connected");
        }
    }

    private JsonNode call(String method, ObjectNode params) throws IOException {
        ObjectNode request = mapper.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", nextId.getAndIncrement());
        request.put("method", method);
        request.set("params", params);
        String json = mapper.writeValueAsString(request);
        writer.write(json);
        writer.newLine();
        writer.flush();
        String line = reader.readLine();
        if (line == null) throw new IOException("MCP server closed connection");
        JsonNode message = mapper.readTree(line);
        return message.path("result");
    }
}
