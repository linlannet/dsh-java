package net.linlan.dsh.cases.extension;

import java.util.List;
import java.util.Map;

/**
 * 扩展管理用例接口。
 * <p>面向触发层的技能/MCP/CLI 扩展管理能力视图。
 */
public interface IExtensionManagementCase {

    List<SkillSummary> listSkills(String cwd);

    SkillInstallOutcome installSkillFromGit(String gitUrl, String subdir, String name);

    SkillInstallOutcome installSkillFromZip(String zipPath, String subdir, String name);

    void removeSkill(String name, String cwd);

    void setSkillEnabled(String name, boolean enabled);

    List<McpServer> listMcpServers();

    McpApplyOutcome upsertMcpServer(McpServer server);

    void removeMcpServer(String name);

    McpApplyOutcome testMcpServer(McpServer server);

    CliConfig getCliConfig();

    CliConfig setCliConfig(CliConfig config);

    record SkillSummary(String name, String description, String source, String path, boolean enabled, boolean removable) {
    }

    record CliConfig(String acpCommand, String acpSubagentCommand, String codexCommand, String claudeCommand, String effectiveNotice) {
    }

    record McpApplyOutcome(boolean success, String name, int toolCount, List<String> tools, String message) {
    }

    record McpServer(String name, String transport, String command, List<String> args, Map<String, String> env, String cwd, String url, Map<String, String> headers, String presetSource) {
    }

    record SkillInstallOutcome(boolean success, String name, String path, String message) {
    }
}
