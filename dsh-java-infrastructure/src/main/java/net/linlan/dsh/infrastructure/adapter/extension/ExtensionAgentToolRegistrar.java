package net.linlan.dsh.infrastructure.adapter.extension;

import net.linlan.dsh.domain.extension.IExtensionManagementPort;
import net.linlan.dsh.domain.model.entity.ToolDefinition;
import net.linlan.dsh.domain.tool.adapter.ToolRegistry;
import net.linlan.dsh.domain.tool.extension.ExtensionTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(value=40)
public class ExtensionAgentToolRegistrar
implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(ExtensionAgentToolRegistrar.class);
    private final ToolRegistry toolRegistry;
    private final IExtensionManagementPort extensionManagement;

    public ExtensionAgentToolRegistrar(ToolRegistry toolRegistry, IExtensionManagementPort extensionManagement) {
        this.toolRegistry = toolRegistry;
        this.extensionManagement = extensionManagement;
    }

    public void run(ApplicationArguments args) {
        this.toolRegistry.register((ToolDefinition)new ExtensionTools.SkillListTool(this.extensionManagement));
        this.toolRegistry.register((ToolDefinition)new ExtensionTools.SkillInstallTool(this.extensionManagement));
        this.toolRegistry.register((ToolDefinition)new ExtensionTools.SkillManageTool(this.extensionManagement));
        this.toolRegistry.register((ToolDefinition)new ExtensionTools.McpListTool(this.extensionManagement));
        this.toolRegistry.register((ToolDefinition)new ExtensionTools.McpConfigureTool(this.extensionManagement));
        this.toolRegistry.register((ToolDefinition)new ExtensionTools.McpRemoveTool(this.extensionManagement));
        log.info("[extension] agent tools registered: extension_skill_list/install/manage, extension_mcp_list/configure/remove");
    }
}

