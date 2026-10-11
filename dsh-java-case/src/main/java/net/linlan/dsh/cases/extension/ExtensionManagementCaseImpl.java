package net.linlan.dsh.cases.extension;

import net.linlan.dsh.domain.extension.IExtensionManagementPort;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 扩展管理用例实现。
 * <p>委托领域扩展管理端口，并转换为触发层可用的视图类型。
 */
@Service
public class ExtensionManagementCaseImpl implements IExtensionManagementCase {

    private static final Logger log = LoggerFactory.getLogger(ExtensionManagementCaseImpl.class);

    private final IExtensionManagementPort extensionManagement;

    public ExtensionManagementCaseImpl(IExtensionManagementPort extensionManagement) {
        this.extensionManagement = extensionManagement;
    }

    @Override
    public List<SkillSummary> listSkills(String cwd) {
        return extensionManagement.listSkills(cwd).stream()
                .map(ExtensionManagementCaseImpl::toSkillSummary)
                .toList();
    }

    @Override
    public SkillInstallOutcome installSkillFromGit(String gitUrl, String subdir, String name) {
        return toInstallOutcome(extensionManagement.installSkillFromGit(gitUrl, subdir, name));
    }

    @Override
    public SkillInstallOutcome installSkillFromZip(String zipPath, String subdir, String name) {
        return toInstallOutcome(extensionManagement.installSkillFromZip(zipPath, subdir, name));
    }

    @Override
    public void removeSkill(String name, String cwd) {
        extensionManagement.removeSkill(name, cwd);
    }

    @Override
    public void setSkillEnabled(String name, boolean enabled) {
        extensionManagement.setSkillEnabled(name, enabled);
    }

    @Override
    public List<McpServer> listMcpServers() {
        return extensionManagement.listMcpServers().stream()
                .map(ExtensionManagementCaseImpl::toMcpServer)
                .toList();
    }

    @Override
    public McpApplyOutcome upsertMcpServer(McpServer server) {
        return toApplyOutcome(extensionManagement.upsertMcpServer(toDomainMcpServer(server)));
    }

    @Override
    public void removeMcpServer(String name) {
        extensionManagement.removeMcpServer(name);
    }

    @Override
    public McpApplyOutcome testMcpServer(McpServer server) {
        return toApplyOutcome(extensionManagement.testMcpServer(toDomainMcpServer(server)));
    }

    @Override
    public CliConfig getCliConfig() {
        return toCliConfig(extensionManagement.getCliConfig());
    }

    @Override
    public CliConfig setCliConfig(CliConfig config) {
        IExtensionManagementPort.ExtensionCliConfig domainConfig = new IExtensionManagementPort.ExtensionCliConfig(
                config.acpCommand(), config.acpSubagentCommand(), config.codexCommand(), config.claudeCommand(), null);
        return toCliConfig(extensionManagement.setCliConfig(domainConfig));
    }

    private static SkillSummary toSkillSummary(IExtensionManagementPort.ExtensionSkillSummary summary) {
        return new SkillSummary(
                summary.name(), summary.description(), summary.source(), summary.path(), summary.enabled(), summary.removable());
    }

    private static SkillInstallOutcome toInstallOutcome(IExtensionManagementPort.ExtensionSkillInstallResult result) {
        return new SkillInstallOutcome(result.success(), result.name(), result.path(), result.message());
    }

    private static McpServer toMcpServer(IExtensionManagementPort.ExtensionMcpServer server) {
        return new McpServer(
                server.name(), server.transport(), server.command(), server.args(), server.env(),
                server.cwd(), server.url(), server.headers(), server.presetSource());
    }

    private static IExtensionManagementPort.ExtensionMcpServer toDomainMcpServer(McpServer view) {
        return new IExtensionManagementPort.ExtensionMcpServer(
                view.name(), view.transport(), view.command(), view.args(), view.env(),
                view.cwd(), view.url(), view.headers(), view.presetSource());
    }

    private static McpApplyOutcome toApplyOutcome(IExtensionManagementPort.ExtensionMcpApplyResult result) {
        return new McpApplyOutcome(result.success(), result.name(), result.toolCount(), result.tools(), result.message());
    }

    private static CliConfig toCliConfig(IExtensionManagementPort.ExtensionCliConfig config) {
        return new CliConfig(
                config.acpCommand(), config.acpSubagentCommand(), config.codexCommand(),
                config.claudeCommand(), config.effectiveNotice());
    }
}
