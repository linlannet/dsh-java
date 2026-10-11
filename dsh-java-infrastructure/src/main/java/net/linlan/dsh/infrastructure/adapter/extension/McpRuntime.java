package net.linlan.dsh.infrastructure.adapter.extension;

import net.linlan.dsh.domain.tool.adapter.ToolRegistry;
import net.linlan.dsh.domain.tool.mcp.IMcpClient;
import net.linlan.dsh.domain.tool.mcp.McpBootstrap;
import net.linlan.dsh.domain.tool.mcp.McpServerConfig;
import net.linlan.dsh.infrastructure.adapter.mcp.HttpMcpClient;
import net.linlan.dsh.infrastructure.adapter.mcp.StdioMcpClient;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class McpRuntime {
    private static final Logger log = LoggerFactory.getLogger(McpRuntime.class);
    private static final long CALL_TIMEOUT_MS = 60000L;
    private static final long TEST_TIMEOUT_MS = 15000L;
    private final ToolRegistry toolRegistry;
    private final McpBootstrap bootstrap;
    private final Map<String, Integer> toolCounts = new ConcurrentHashMap<String, Integer>();

    public McpRuntime(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
        this.bootstrap = new McpBootstrap(toolRegistry, this::createClient);
    }

    public int connectAll(List<McpServerConfig> configs) {
        return this.bootstrap.connectAll(configs);
    }

    public int connect(McpServerConfig config) {
        int count = this.bootstrap.connectAndRegister(config);
        if (!this.bootstrap.isConnected(config.name())) {
            List errors = this.bootstrap.errors();
            String lastError = errors.isEmpty() ? "\u672a\u77e5\u9519\u8bef" : (String)errors.get(errors.size() - 1);
            throw new IllegalStateException("MCP server \u8fde\u63a5\u5931\u8d25: " + lastError);
        }
        this.toolCounts.put(config.name(), count);
        return count;
    }

    public boolean disconnect(String name) {
        boolean removed = this.bootstrap.disconnectServer(name);
        this.toolCounts.remove(name);
        return removed;
    }

    public void disconnectAll() {
        this.bootstrap.disconnectAll();
        this.toolCounts.clear();
    }

    public boolean isConnected(String name) {
        return this.bootstrap.isConnected(name);
    }

    public Map<String, Integer> status() {
        LinkedHashMap<String, Integer> status = new LinkedHashMap<String, Integer>();
        for (String name : this.bootstrap.connectedServers()) {
            status.put(name, this.toolCounts.getOrDefault(name, 0));
        }
        return status;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public ExtensionMcpProbe probe(McpServerConfig config) {
        IMcpClient client = this.createClient(config);
        try {
            client.connect().get(15000L, TimeUnit.SECONDS);
            List<IMcpClient.McpToolDescriptor> tools = (List<IMcpClient.McpToolDescriptor>)client.listTools().get(15000L, TimeUnit.SECONDS);
            List<String> names = tools.stream().map(IMcpClient.McpToolDescriptor::name).toList();
            ExtensionMcpProbe extensionMcpProbe = new ExtensionMcpProbe(true, names, "");
            return extensionMcpProbe;
        }
        catch (Exception e) {
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            ExtensionMcpProbe extensionMcpProbe = new ExtensionMcpProbe(false, List.of(), message);
            return extensionMcpProbe;
        }
        finally {
            try {
                client.disconnect();
            }
            catch (Exception exception) {}
        }
    }

    private IMcpClient createClient(McpServerConfig config) {
        if ("stdio".equals(config.transport())) {
            return new StdioMcpClient(config.command(), config.args(), config.env(), config.cwd(), 60000L);
        }
        return new HttpMcpClient(config.transport(), config.url(), config.headers(), 60000L);
    }

    public record ExtensionMcpProbe(boolean success, List<String> tools, String message) {
    }
}

