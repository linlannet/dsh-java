package net.linlan.dsh.domain.lsp.stdio;

import net.linlan.dsh.domain.lsp.model.valobj.LspLocation;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * LspStdioProvider 表示通过 stdio JSON-RPC 与 LSP server 通信的领域提供方。
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
public class LspStdioProvider {

    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicLong nextId = new AtomicLong(1);
    private String serverCommand;
    private BufferedReader reader;
    private BufferedWriter writer;
    private boolean initialized;

    /**
     * 处理Lsp Stdio Provider。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @return 方法执行结果。
     */
    public LspStdioProvider() {}

    /**
     * 处理Lsp Stdio Provider。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param reader reader参数。
     * @param writer writer参数。
     * @return 方法执行结果。
     */
    LspStdioProvider(BufferedReader reader, BufferedWriter writer) {
        this.reader = reader;
        this.writer = writer;
        this.initialized = true;
    }

    /**
     * 处理initialize。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param serverCommand server Command参数。
     * @return 方法执行结果。
     */
    public void initialize(String serverCommand) throws IOException {
        if (serverCommand == null || serverCommand.isBlank()) {
            throw new IllegalArgumentException("serverCommand must not be blank");
        }
        this.serverCommand = serverCommand;
        List<String> cmd = new ArrayList<>();
        cmd.add("sh");
        cmd.add("-c");
        cmd.add(serverCommand);
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(false);
        Process process = pb.start();
        this.writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
        this.reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        sendInitialize();
        this.initialized = true;
    }

    private void sendInitialize() throws IOException {
        ObjectNode params = mapper.createObjectNode();
        params.put("processId", ProcessHandle.current().pid());
        params.put("rootUri", "");
        params.putNull("capabilities");
        ObjectNode request = newRequest("initialize", params);
        writeJson(request);
        readResponse();
    }

    /**
     * 处理definition。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param file file参数。
     * @param line line参数。
     * @param col col参数。
     * @return 方法执行结果。
     */
    public Optional<LspLocation> definition(String file, int line, int col) {
        ensureInitialized();
        ObjectNode params = locationParams(file, line, col);
        JsonNode result = call("textDocument/definition", params);
        return parseFirstLocation(result);
    }

    /**
     * 处理references。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param file file参数。
     * @param line line参数。
     * @param col col参数。
     * @return 方法执行结果。
     */
    public List<LspLocation> references(String file, int line, int col) {
        ensureInitialized();
        ObjectNode params = locationParams(file, line, col);
        ObjectNode context = mapper.createObjectNode();
        context.put("includeDeclaration", false);
        params.set("context", context);
        JsonNode result = call("textDocument/references", params);
        return parseLocations(result);
    }

    /**
     * 处理diagnostics。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param file file参数。
     * @return 方法执行结果。
     */
    public List<String> diagnostics(String file) {
        ensureInitialized();
        ObjectNode params = mapper.createObjectNode();
        params.set("textDocument", textDocumentIdentifier(file));
        JsonNode result = call("textDocument/diagnostic", params);
        List<String> messages = new ArrayList<>();
        if (result != null && result.isArray()) {
            for (JsonNode d : result) {
                messages.add(d.path("message").asText(""));
            }
        }
        return messages;
    }

    /**
     * 判断Initialized是否成立。
     * @return 领域处理结果。
     */
    public boolean isInitialized() { return initialized; }

    /**
     * 返回server Command。
     * @return 方法执行结果。
     */
    public String getServerCommand() { return serverCommand; }

    private void ensureInitialized() {
        if (!initialized) throw new IllegalStateException("LSP provider not initialized");
    }

    private ObjectNode locationParams(String file, int line, int col) {
        ObjectNode params = mapper.createObjectNode();
        params.set("textDocument", textDocumentIdentifier(file));
        ObjectNode position = mapper.createObjectNode();
        position.put("line", line);
        position.put("character", col);
        params.set("position", position);
        return params;
    }

    private ObjectNode textDocumentIdentifier(String file) {
        ObjectNode td = mapper.createObjectNode();
        td.put("uri", file.startsWith("file://") ? file : "file://" + file);
        return td;
    }

    private JsonNode call(String method, ObjectNode params) {
        try {
            ObjectNode request = newRequest(method, params);
            writeJson(request);
            return readResponse();
        } catch (IOException e) {
            throw new RuntimeException("LSP call failed: " + e.getMessage(), e);
        }
    }

    private ObjectNode newRequest(String method, ObjectNode params) {
        ObjectNode request = mapper.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", nextId.getAndIncrement());
        request.put("method", method);
        request.set("params", params);
        return request;
    }

    private void writeJson(ObjectNode request) throws IOException {
        String json = mapper.writeValueAsString(request);
        writer.write(json);
        writer.newLine();
        writer.flush();
    }

    private JsonNode readResponse() throws IOException {
        String line = reader.readLine();
        if (line == null) throw new IOException("LSP server closed connection");
        JsonNode message = mapper.readTree(line);
        if (message.has("error")) {
            throw new RuntimeException("LSP error: " + message.path("error").path("message").asText());
        }
        return message.path("result");
    }

    private Optional<LspLocation> parseFirstLocation(JsonNode result) {
        List<LspLocation> locs = parseLocations(result);
        return locs.isEmpty() ? Optional.empty() : Optional.of(locs.get(0));
    }

    private List<LspLocation> parseLocations(JsonNode result) {
        List<LspLocation> locations = new ArrayList<>();
        if (result == null) return locations;
        if (result.isArray()) {
            for (JsonNode node : result) {
                locations.add(parseLocation(node));
            }
        } else if (result.isObject() && result.has("uri")) {
            locations.add(parseLocation(result));
        }
        return locations;
    }

    private LspLocation parseLocation(JsonNode node) {
        String uri = node.path("uri").asText("");
        JsonNode range = node.path("range");
        JsonNode start = range.path("start");
        JsonNode end = range.path("end");
        return new LspLocation(
                uri,
                start.path("line").asInt(0),
                start.path("character").asInt(0),
                end.path("line").asInt(0),
                end.path("character").asInt(0)
        );
    }
}
