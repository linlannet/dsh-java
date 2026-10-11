package net.linlan.dsh.domain.tool.plugin;

import net.linlan.dsh.domain.model.valobj.ContentBlock;

import java.util.*;
import java.util.concurrent.*;

/**
 * IPluginToolBridge 表示IPluginToolBridge。
 */
public interface IPluginToolBridge {

    /**
     * 处理connect。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param pluginId 插件ID。
     * @return 领域处理结果。
     */
    CompletableFuture<Void> connect(String pluginId, Process process);

    /**
     * 处理disconnect。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param pluginId 插件ID。
     * @return 领域处理结果。
     */
    CompletableFuture<Void> disconnect(String pluginId);

    /**
     * 判断Connected是否成立。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param pluginId 插件ID。
     * @return 领域处理结果。
     */
    boolean isConnected(String pluginId);

    /**
     * 处理listToolsTool列表。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param pluginId 插件ID。
     * @return 领域处理结果。
     */
    CompletableFuture<List<PluginToolDescriptor>> listTools(String pluginId);

    /**
     * PluginToolDescriptor 表示PluginToolDescriptor。
     */
    CompletableFuture<PluginToolResult> callTool(String pluginId, String toolName, Map<String, Object> arguments);

    /**
     * PluginToolDescriptor 表示PluginToolDescriptor。
     */
    record PluginToolDescriptor(
            String name,
            String description,
            Map<String, Object> inputSchema
    ) {}

    /**
     * PluginToolResult 表示PluginToolResult结果。
     */
    record PluginToolResult(
            List<ContentBlock> content,
            boolean isError,
            String errorText
    ) {}
}
