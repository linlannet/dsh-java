package net.linlan.dsh.domain.extension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface IExtensionManagementPort {
    public List<ExtensionSkillSummary> listSkills(String var1);

    public ExtensionSkillInstallResult installSkillFromGit(String var1, String var2, String var3);

    public ExtensionSkillInstallResult installSkillFromZip(String var1, String var2, String var3);

    public void removeSkill(String var1, String var2);

    public void setSkillEnabled(String var1, boolean var2);

    public List<ExtensionMcpServer> listMcpServers();

    public ExtensionMcpApplyResult upsertMcpServer(ExtensionMcpServer var1);

    public void removeMcpServer(String var1);

    public ExtensionMcpApplyResult testMcpServer(ExtensionMcpServer var1);

    public ExtensionCliConfig getCliConfig();

    public ExtensionCliConfig setCliConfig(ExtensionCliConfig var1);

    default public Optional<String> findSkillPath(String name, String cwd) {
        return this.listSkills(cwd).stream().filter(s -> s.name().equals(name)).map(ExtensionSkillSummary::path).filter(p -> p != null && !p.isBlank()).findFirst();
    }

    public record ExtensionSkillSummary(String name, String description, String source, String path, boolean enabled, boolean removable) {
    }

    public record ExtensionCliConfig(String acpCommand, String acpSubagentCommand, String codexCommand, String claudeCommand, String effectiveNotice) {
        public ExtensionCliConfig {
            acpCommand = acpCommand == null ? "" : acpCommand;
            acpSubagentCommand = acpSubagentCommand == null ? "" : acpSubagentCommand;
            codexCommand = codexCommand == null ? "" : codexCommand;
            claudeCommand = claudeCommand == null ? "" : claudeCommand;
        }
    }

    public record ExtensionMcpApplyResult(boolean success, String name, int toolCount, List<String> tools, String message) {
    }

    public record ExtensionMcpServer(String name, String transport, String command, List<String> args, Map<String, String> env, String cwd, String url, Map<String, String> headers, String presetSource) {
    }

    public record ExtensionSkillInstallResult(boolean success, String name, String path, String message) {
    }
}

