package net.linlan.dsh.infrastructure.config;

import net.linlan.dsh.domain.agent.service.subagent.SubagentRegistry;
import net.linlan.dsh.domain.skill.ISkillService;
import net.linlan.dsh.domain.tool.adapter.ToolRegistry;
import net.linlan.dsh.infrastructure.adapter.extension.ExtensionConfigStore;
import net.linlan.dsh.infrastructure.adapter.extension.ExtensionManagementService;
import net.linlan.dsh.infrastructure.adapter.extension.McpRuntime;
import net.linlan.dsh.infrastructure.adapter.skill.FilesystemSkillProviderPort;
import net.linlan.dsh.infrastructure.adapter.subagent.AcpSubagentProvider;
import net.linlan.dsh.infrastructure.adapter.subagent.ClaudeCodeSubagentProvider;
import net.linlan.dsh.infrastructure.adapter.subagent.CodexSubagentProvider;
import net.linlan.dsh.infrastructure.config.HarnessExtensionsProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ExtensionBeanConfig {
    @Bean
    public ExtensionConfigStore extensionConfigStore(ObjectMapper objectMapper, @Value(value="${harness.config.dir:${user.home}/.dsh}") String configDir) {
        ExtensionConfigStore store = new ExtensionConfigStore(objectMapper, Path.of(configDir, "extensions.json"));
        store.init();
        return store;
    }

    @Bean
    public McpRuntime mcpRuntime(ToolRegistry toolRegistry) {
        return new McpRuntime(toolRegistry);
    }

    @Bean
    public ExtensionManagementService extensionManagementService(ISkillService skillService, FilesystemSkillProviderPort skillProviderPort, HarnessExtensionsProperties properties, ExtensionConfigStore store, McpRuntime mcpRuntime, SubagentRegistry subagentRegistry, ObjectProvider<ClaudeCodeSubagentProvider> claudeProvider, ObjectProvider<CodexSubagentProvider> codexProvider, ObjectProvider<AcpSubagentProvider> acpProvider, @Value(value="${harness.acp.command:}") String ymlAcpCommand, @Value(value="${harness.subagent.acp.command:}") String ymlAcpSubagentCommand, @Value(value="${harness.subagent.codex.command:codex}") String ymlCodexCommand, @Value(value="${harness.subagent.claude-code.command:claude}") String ymlClaudeCommand) {
        return new ExtensionManagementService(skillService, skillProviderPort, properties, store, mcpRuntime, subagentRegistry, claudeProvider, codexProvider, acpProvider, ymlAcpCommand, ymlAcpSubagentCommand, ymlCodexCommand, ymlClaudeCommand);
    }
}

