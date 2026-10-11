package net.linlan.dsh.domain.tool.mcp;

import net.linlan.dsh.domain.tool.adapter.ToolRegistry;
import net.linlan.dsh.domain.tool.mcp.model.valobj.ReconnectPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/**
 * MCP 引导器：按配置连接 MCP server，并把远端工具以
 * {@code mcp__<server>__<tool>} 形式注册进共享 {@link ToolRegistry}。
 * <p>
 * 执行流程：
 * 1. 遍历 server 配置；
 * 2. 用客户端工厂创建 client（stdio 传输）并 connect + listTools；
 * 3. 每个远端工具包装成 {@link McpToolAdapter} 注册；
 * 4. 任一 server 失败不影响其余 server（记录到 {@link #errors()}）。
 * <p>
 * 对齐原版 TS mcp-client 的「连接监督 + 工具注入 registry」闭环；
 * HTTP/SSE 传输在客户端工厂层扩展（当前仅 stdio 实现）。
 *
 *
 */
public final class McpBootstrap {

    /** 客户端工厂（由基础设施层提供 stdio 实现）。 */
    @FunctionalInterface
    public interface McpClientFactory {
        IMcpClient create(McpServerConfig config);
    }

    private static final long DEFAULT_CALL_TIMEOUT_MS = 60_000;

    private final ToolRegistry registry;
    private final McpClientFactory clientFactory;
    private final ReconnectPolicy reconnectPolicy;
    private final Map<String, IMcpClient> clients = new ConcurrentHashMap<>();
    private final Map<String, List<Runnable>> disposers = new ConcurrentHashMap<>();
    private final List<String> errors = new CopyOnWriteArrayList<>();

    public McpBootstrap(ToolRegistry registry, McpClientFactory clientFactory) {
        this(registry, clientFactory, ReconnectPolicy.default_());
    }

    public McpBootstrap(ToolRegistry registry, McpClientFactory clientFactory, ReconnectPolicy reconnectPolicy) {
        this.registry = registry;
        this.clientFactory = clientFactory;
        this.reconnectPolicy = reconnectPolicy;
    }

    /**
     * 连接并注册一个 MCP server 的全部工具。
     *
     * @return 注册的工具数
     */
    public int connectAndRegister(McpServerConfig config) {
        if (disposers.containsKey(config.name())) {
            disconnectServer(config.name());
        }
        IMcpClient rawClient = clientFactory.create(config);
        IMcpClient client = new ReconnectingMcpClient(config.name(), rawClient, reconnectPolicy);
        try {
            client.connect().get(30, TimeUnit.SECONDS);
            List<IMcpClient.McpToolDescriptor> tools = client.listTools().get(30, TimeUnit.SECONDS);
            clients.put(config.name(), client);
            List<Runnable> serverDisposers = new ArrayList<>(tools.size());
            for (IMcpClient.McpToolDescriptor tool : tools) {
                serverDisposers.add(registry.register(new McpToolAdapter(config.name(), tool, client, DEFAULT_CALL_TIMEOUT_MS)));
            }
            disposers.put(config.name(), serverDisposers);
            return tools.size();
        } catch (Exception e) {
            errors.add(config.name() + ": " + e.getMessage());
            try { client.disconnect(); } catch (Exception ignored) { }
            return 0;
        }
    }

    /** 逐个连接全部配置的 server，返回注册的工具总数。 */
    public int connectAll(List<McpServerConfig> configs) {
        int total = 0;
        for (McpServerConfig config : configs) {
            total += connectAndRegister(config);
        }
        return total;
    }

    /** 断开指定 server 的连接，并取消其已注册的工具。 */
    public boolean disconnectServer(String name) {
        List<Runnable> serverDisposers = disposers.remove(name);
        IMcpClient client = clients.remove(name);
        if (serverDisposers == null && client == null) {
            return false;
        }
        if (serverDisposers != null) {
            serverDisposers.forEach(Runnable::run);
        }
        if (client != null) {
            try { client.disconnect(); } catch (Exception ignored) { }
        }
        return true;
    }

    /** 判断指定 server 是否已连接。 */
    public boolean isConnected(String name) {
        return clients.containsKey(name);
    }

    /** 已连接的 server 名称列表。 */
    public List<String> connectedServers() {
        return List.copyOf(clients.keySet());
    }

    /** 断开全部连接。 */
    public void disconnectAll() {
        for (String name : List.copyOf(clients.keySet())) {
            disconnectServer(name);
        }
    }

    /** 各 server 的连接错误（连接成功的不在其中）。 */
    public List<String> errors() {
        return List.copyOf(errors);
    }
}
