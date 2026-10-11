package net.linlan.dsh.domain.tool.plugin;

import net.linlan.dsh.domain.tool.adapter.ToolRegistry;
import net.linlan.dsh.domain.agent.service.ScopedSystemPromptAssembler;
import net.linlan.dsh.domain.agent.service.SystemPromptAssembler;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * PluginToolBridgeService 封装PluginToolBridge的领域规则与流程。
 */
@Service
public class PluginToolBridgeService {

    private final ToolRegistry toolRegistry;
    private final IPluginToolBridge pluginToolBridge;
    private final SystemPromptAssembler promptAssembler;
    private final Map<String, List<Runnable>> pluginDisposers = new ConcurrentHashMap<>();
    private final Map<String, List<String>> pluginToolNames = new ConcurrentHashMap<>();

    /**
     * 处理插件 工具 Bridge Service。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param toolRegistry 工具 Registry参数。
     * @param pluginToolBridge 插件 工具 Bridge参数。
     * @return 方法执行结果。
     */
    public PluginToolBridgeService(ToolRegistry toolRegistry, IPluginToolBridge pluginToolBridge) {
        this(toolRegistry, pluginToolBridge, null);
    }

    @Autowired
    public PluginToolBridgeService(
            ToolRegistry toolRegistry,
            IPluginToolBridge pluginToolBridge,
            SystemPromptAssembler promptAssembler
    ) {
        this.toolRegistry = toolRegistry;
        this.pluginToolBridge = pluginToolBridge;
        this.promptAssembler = promptAssembler;
    }

    /**
     * 处理bridgePlugin。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param pluginId 插件ID。
     * @return 领域处理结果。
     */
    public List<String> bridgePlugin(String pluginId, Process process) {
        if (pluginDisposers.containsKey(pluginId)) {
            return List.copyOf(pluginToolNames.getOrDefault(pluginId, List.of()));
        }
        try {
            // Connect
            pluginToolBridge.connect(pluginId, process).join();

            // List tools
            List<IPluginToolBridge.PluginToolDescriptor> descriptors =
                    pluginToolBridge.listTools(pluginId).join();

            // Register each tool
            List<Runnable> disposers = new ArrayList<>();
            List<String> toolNames = new ArrayList<>();

            for (IPluginToolBridge.PluginToolDescriptor desc : descriptors) {
                PluginToolDefinition toolDef = new PluginToolDefinition(pluginId, pluginToolBridge, desc);
                Runnable disposer = toolRegistry.register(toolDef);
                disposers.add(disposer);
                toolNames.add(toolDef.name());
            }

            List<String> previousToolNames = pluginToolNames.putIfAbsent(pluginId, List.copyOf(toolNames));
            if (previousToolNames != null) {
                for (Runnable disposer : disposers) {
                    disposer.run();
                }
                return previousToolNames;
            }
            pluginDisposers.put(pluginId, disposers);
            registerPromptSection(pluginId, descriptors, toolNames);
            return toolNames;

        } catch (Exception e) {
            throw new RuntimeException("Plugin bridge failed: " + e.getMessage(), e);
        }
    }

    /**
     * 处理unbridgePlugin。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param pluginId 插件ID。
     * @return 领域处理结果。
     */
    public void unbridgePlugin(String pluginId) {
        removePromptSection(pluginId);
        pluginToolNames.remove(pluginId);
        List<Runnable> disposers = pluginDisposers.remove(pluginId);
        if (disposers != null) {
            for (Runnable disposer : disposers) {
                disposer.run();
            }
        }

        try {
            pluginToolBridge.disconnect(pluginId).join();
        } catch (Exception ignored) {
        }
    }

    /**
     * 判断Bridged是否成立。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param pluginId 插件ID。
     * @return 领域处理结果。
     */
    public boolean isBridged(String pluginId) {
        return pluginDisposers.containsKey(pluginId);
    }

    /**
     * 获取ToolNames名称列表。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param pluginId 插件ID。
     * @return 领域处理结果。
     */
    public List<String> getToolNames(String pluginId) {
        return List.copyOf(pluginToolNames.getOrDefault(pluginId, List.of()));
    }

    /**
     * 注册插件贡献的系统提示词片段。
     * <p>流程：校验片段来源 → 登记到提示词装配器 → 供下一轮模型调用使用。</p>
     */
    private void registerPromptSection(
            String pluginId,
            List<IPluginToolBridge.PluginToolDescriptor> descriptors,
            List<String> registeredToolNames
    ) {
        if (!(promptAssembler instanceof ScopedSystemPromptAssembler scoped) || descriptors.isEmpty()) {
            return;
        }
        StringJoiner lines = new StringJoiner("\n");
        for (int i = 0; i < descriptors.size(); i++) {
            var descriptor = descriptors.get(i);
            // 提示词中必须使用注册后的完整工具名（plugin__<pluginId>__<tool>），
            // 与 function calling schema 中的名字保持一致，避免模型按未加前缀的名字调用失败。
            String toolName = i < registeredToolNames.size()
                    ? registeredToolNames.get(i)
                    : descriptor.name();
            lines.add("- " + toolName + ": "
            + sanitizePromptText(descriptor.description() == null || descriptor.description().isBlank()
                    ? "no description" : descriptor.description()));
        }
        String text = "## Plugin capabilities: " + pluginId + "\n"
                + "These plugin tools are available in the tool schema. "
                + "Use the matching tool when the request needs its capability.\n"
                + lines;
        scoped.registerSection(new ScopedSystemPromptAssembler.PromptSection(
                promptSectionName(pluginId), 150, text));
    }

    /**
     * 移除 提示词 Section。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param pluginId 插件 ID。
     */
    private void removePromptSection(String pluginId) {
        if (promptAssembler instanceof ScopedSystemPromptAssembler scoped) {
            scoped.removeSection(promptSectionName(pluginId));
        }
    }

    /**
     * 发起提示词 Section 名称。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param pluginId 插件 ID。
     * @return 方法执行结果。
     */
    private String promptSectionName(String pluginId) {
        return "plugin:" + pluginId + ":capabilities";
    }

    /**
     * 清理插件贡献的描述文本，防止换行伪装成提示词结构注入系统提示词。
     */
    private String sanitizePromptText(String value) {
        return value.replaceAll("[\\r\\n\\t]+", " ").trim();
    }
}
