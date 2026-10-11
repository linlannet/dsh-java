package net.linlan.dsh.infrastructure.adapter.extension;

import net.linlan.dsh.domain.agent.service.subagent.SubagentProvider;
import net.linlan.dsh.domain.agent.service.subagent.SubagentRegistry;
import net.linlan.dsh.domain.extension.IExtensionManagementPort;
import net.linlan.dsh.domain.skill.ISkillService;
import net.linlan.dsh.domain.skill.model.entity.SkillSummary;
import net.linlan.dsh.domain.tool.mcp.McpServerConfig;
import net.linlan.dsh.infrastructure.adapter.extension.ExtensionConfigStore;
import net.linlan.dsh.infrastructure.adapter.extension.McpRuntime;
import net.linlan.dsh.infrastructure.adapter.skill.FilesystemSkillProviderPort;
import net.linlan.dsh.infrastructure.adapter.subagent.AcpSubagentProvider;
import net.linlan.dsh.infrastructure.adapter.subagent.ClaudeCodeSubagentProvider;
import net.linlan.dsh.infrastructure.adapter.subagent.CodexSubagentProvider;
import net.linlan.dsh.infrastructure.config.HarnessExtensionsProperties;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileAttribute;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;

@Service
public class ExtensionManagementService
implements IExtensionManagementPort,
ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(ExtensionManagementService.class);
    private final ISkillService skillService;
    private final FilesystemSkillProviderPort skillProviderPort;
    private final HarnessExtensionsProperties properties;
    private final ExtensionConfigStore store;
    private final McpRuntime mcpRuntime;
    private final SubagentRegistry subagentRegistry;
    private final ObjectProvider<ClaudeCodeSubagentProvider> claudeProvider;
    private final ObjectProvider<CodexSubagentProvider> codexProvider;
    private final ObjectProvider<AcpSubagentProvider> acpProvider;
    private final String ymlAcpCommand;
    private final String ymlAcpSubagentCommand;
    private final String ymlCodexCommand;
    private final String ymlClaudeCommand;

    public ExtensionManagementService(ISkillService skillService, FilesystemSkillProviderPort skillProviderPort, HarnessExtensionsProperties properties, ExtensionConfigStore store, McpRuntime mcpRuntime, SubagentRegistry subagentRegistry, ObjectProvider<ClaudeCodeSubagentProvider> claudeProvider, ObjectProvider<CodexSubagentProvider> codexProvider, ObjectProvider<AcpSubagentProvider> acpProvider, @Value(value="${harness.acp.command:}") String ymlAcpCommand, @Value(value="${harness.subagent.acp.command:}") String ymlAcpSubagentCommand, @Value(value="${harness.subagent.codex.command:codex}") String ymlCodexCommand, @Value(value="${harness.subagent.claude-code.command:claude}") String ymlClaudeCommand) {
        this.skillService = skillService;
        this.skillProviderPort = skillProviderPort;
        this.properties = properties;
        this.store = store;
        this.mcpRuntime = mcpRuntime;
        this.subagentRegistry = subagentRegistry;
        this.claudeProvider = claudeProvider;
        this.codexProvider = codexProvider;
        this.acpProvider = acpProvider;
        this.ymlAcpCommand = ymlAcpCommand;
        this.ymlAcpSubagentCommand = ymlAcpSubagentCommand;
        this.ymlCodexCommand = ymlCodexCommand;
        this.ymlClaudeCommand = ymlClaudeCommand;
    }

    public void run(ApplicationArguments args) {
        this.applyDisabledSkills();
        log.info("[extension] management ready (store={})", (Object)this.store.file());
    }

    private void applyDisabledSkills() {
        this.skillProviderPort.setDisabledNames(new HashSet<String>(this.store.snapshot().disabledSkills));
    }

    public List<IExtensionManagementPort.ExtensionSkillSummary> listSkills(String cwd) {
        HashSet<String> disabled = new HashSet<String>(this.store.snapshot().disabledSkills);
        Path userRoot = this.skillProviderPort.dshHome().resolve("skills");
        ArrayList<Path> managedRoots = new ArrayList<Path>();
        managedRoots.add(userRoot);
        managedRoots.addAll(this.skillProviderPort.customRoots());
        ArrayList<IExtensionManagementPort.ExtensionSkillSummary> result = new ArrayList<IExtensionManagementPort.ExtensionSkillSummary>();
        for (SkillSummary skill : this.skillService.list(cwd)) {
            String path = skill.path().orElse("");
            result.add(this.toSummary(skill.name(), skill.description(), skill.source(), path, disabled, managedRoots));
        }
        HashSet<String> listed = new HashSet<String>();
        result.forEach(s -> listed.add(s.name()));
        block1: for (String name : disabled) {
            if (listed.contains(name)) continue;
            for (Path root : managedRoots) {
                Path dirSkill = root.resolve(name).resolve("SKILL.md");
                Path fileSkill = root.resolve(name + ".md");
                if (Files.isRegularFile(dirSkill, new LinkOption[0])) {
                    result.add(this.toSummary(name, this.readSkillDescription(dirSkill), "CUSTOM(disabled)", dirSkill.toString(), disabled, managedRoots));
                    listed.add(name);
                    continue block1;
                }
                if (!Files.isRegularFile(fileSkill, new LinkOption[0])) continue;
                result.add(this.toSummary(name, this.readSkillDescription(fileSkill), "CUSTOM(disabled)", fileSkill.toString(), disabled, managedRoots));
                listed.add(name);
                continue block1;
            }
        }
        return result;
    }

    private IExtensionManagementPort.ExtensionSkillSummary toSummary(String name, String description, Object source, String path, Set<String> disabled, List<Path> managedRoots) {
        boolean removable = false;
        if (path != null && !path.isBlank()) {
            Path skillPath = Path.of(path, new String[0]);
            for (Path root : managedRoots) {
                if (!skillPath.startsWith(root)) continue;
                removable = true;
                break;
            }
        }
        return new IExtensionManagementPort.ExtensionSkillSummary(name, description, source == null ? "" : String.valueOf(source), path, !disabled.contains(name), removable);
    }

    private String readSkillDescription(Path file) {
        try {
            List<String> lines = Files.readAllLines(file);
            boolean inFrontmatter = false;
            for (int i = 0; i < Math.min(lines.size(), 20); ++i) {
                String line = lines.get(i).trim();
                if (i == 0 && "---".equals(line)) {
                    inFrontmatter = true;
                    continue;
                }
                if (inFrontmatter && "---".equals(line)) break;
                if (!inFrontmatter || !line.startsWith("description:")) continue;
                return line.substring("description:".length()).trim();
            }
            return "";
        }
        catch (IOException e) {
            return "";
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     * Enabled aggressive block sorting
     * Enabled unnecessary exception pruning
     * Enabled aggressive exception aggregation
     */
    public IExtensionManagementPort.ExtensionSkillInstallResult installSkillFromGit(String gitUrl, String subdir, String name) {
        if (gitUrl == null) return new IExtensionManagementPort.ExtensionSkillInstallResult(false, "", "", "gitUrl \u4e0d\u80fd\u4e3a\u7a7a");
        if (gitUrl.isBlank()) {
            return new IExtensionManagementPort.ExtensionSkillInstallResult(false, "", "", "gitUrl \u4e0d\u80fd\u4e3a\u7a7a");
        }
        Path tmpDir = null;
        try {
            Path workRoot;
            tmpDir = workRoot = Files.createTempDirectory("dsh-skill-clone-", new FileAttribute[0]);
            ProcessBuilder pb = new ProcessBuilder("git", "clone", "--depth", "1", gitUrl.trim(), workRoot.resolve("repo").toString());
            pb.environment().put("GIT_TERMINAL_PROMPT", "0");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            byte[] output = process.getInputStream().readAllBytes();
            int exit = process.waitFor();
            if (exit != 0) {
                IExtensionManagementPort.ExtensionSkillInstallResult extensionSkillInstallResult = new IExtensionManagementPort.ExtensionSkillInstallResult(false, "", "", "git clone \u5931\u8d25\uff08exit " + exit + "\uff09: " + new String(output).trim());
                return extensionSkillInstallResult;
            }
            Path repo = workRoot.resolve("repo");
            Path skillDir = this.locateSkillDir(repo, subdir);
            if (skillDir == null) {
                IExtensionManagementPort.ExtensionSkillInstallResult extensionSkillInstallResult = new IExtensionManagementPort.ExtensionSkillInstallResult(false, "", "", "\u4ed3\u5e93\u4e2d\u672a\u627e\u5230 SKILL.md\uff08\u53ef\u6307\u5b9a subdir \u6307\u5411\u6280\u80fd\u76ee\u5f55\uff09");
                return extensionSkillInstallResult;
            }
            String skillName = this.resolveSkillName(name, skillDir, repo, gitUrl);
            Path target = this.installRoot().resolve(skillName);
            if (Files.exists(target, new LinkOption[0])) {
                ExtensionConfigStore.deleteRecursively(target);
            }
            this.copyDirectory(skillDir, target);
            String message = "\u6280\u80fd\u5df2\u5b89\u88c5: " + skillName;
            log.info("[extension] {}", (Object)message);
            IExtensionManagementPort.ExtensionSkillInstallResult extensionSkillInstallResult = new IExtensionManagementPort.ExtensionSkillInstallResult(true, skillName, target.toString(), message);
            return extensionSkillInstallResult;
        }
        catch (Exception e) {
            log.warn("[extension] skill install failed: {}", (Object)e.getMessage());
            IExtensionManagementPort.ExtensionSkillInstallResult extensionSkillInstallResult = new IExtensionManagementPort.ExtensionSkillInstallResult(false, "", "", "\u5b89\u88c5\u5931\u8d25: " + e.getMessage());
            return extensionSkillInstallResult;
        }
        finally {
            if (tmpDir != null) {
                try {
                    ExtensionConfigStore.deleteRecursively(tmpDir);
                }
                catch (IOException iOException) {}
            }
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public IExtensionManagementPort.ExtensionSkillInstallResult installSkillFromZip(String zipPath, String subdir, String name) {
        IExtensionManagementPort.ExtensionSkillInstallResult extensionSkillInstallResult;
        if (zipPath == null || zipPath.isBlank()) {
            return new IExtensionManagementPort.ExtensionSkillInstallResult(false, "", "", "zipPath \u4e0d\u80fd\u4e3a\u7a7a");
        }
        Path zipFile = Path.of(zipPath.trim(), new String[0]);
        if (!Files.isRegularFile(zipFile, new LinkOption[0])) {
            return new IExtensionManagementPort.ExtensionSkillInstallResult(false, "", "", "\u538b\u7f29\u5305\u4e0d\u5b58\u5728: " + zipPath);
        }
        Path tmpDir = null;
        try {
            Path workRoot;
            tmpDir = workRoot = Files.createTempDirectory("dsh-skill-zip-", new FileAttribute[0]);
            Path extractRoot = workRoot.resolve("unpacked");
            this.unzip(zipFile, extractRoot);
            Path skillDir = this.locateSkillDir(extractRoot, subdir);
            if (skillDir == null) {
                IExtensionManagementPort.ExtensionSkillInstallResult extensionSkillInstallResult2 = new IExtensionManagementPort.ExtensionSkillInstallResult(false, "", "", "\u538b\u7f29\u5305\u4e2d\u672a\u627e\u5230 SKILL.md\uff08\u53ef\u6307\u5b9a subdir \u6307\u5411\u6280\u80fd\u76ee\u5f55\uff09");
                return extensionSkillInstallResult2;
            }
            String skillName = this.resolveSkillName(name, skillDir, extractRoot, this.zipFileName(zipFile));
            Path target = this.installRoot().resolve(skillName);
            if (Files.exists(target, new LinkOption[0])) {
                ExtensionConfigStore.deleteRecursively(target);
            }
            this.copyDirectory(skillDir, target);
            String message = "\u6280\u80fd\u5df2\u5b89\u88c5: " + skillName;
            log.info("[extension] {}", (Object)message);
            extensionSkillInstallResult = new IExtensionManagementPort.ExtensionSkillInstallResult(true, skillName, target.toString(), message);
        }
        catch (Exception e) {
            log.warn("[extension] skill zip install failed: {}", (Object)e.getMessage());
            IExtensionManagementPort.ExtensionSkillInstallResult extensionSkillInstallResult3 = new IExtensionManagementPort.ExtensionSkillInstallResult(false, "", "", "\u5b89\u88c5\u5931\u8d25: " + e.getMessage());
            return extensionSkillInstallResult3;
        }
        finally {
            if (tmpDir != null) {
                try {
                    ExtensionConfigStore.deleteRecursively(tmpDir);
                }
                catch (IOException iOException) {}
            }
        }
        return extensionSkillInstallResult;
    }

    private void unzip(Path zipFile, Path destDir) throws IOException {
        Files.createDirectories(destDir, new FileAttribute[0]);
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile, new OpenOption[0]));){
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path target = destDir.resolve(entry.getName()).normalize();
                if (!target.startsWith(destDir)) {
                    throw new IOException("\u538b\u7f29\u5305\u5305\u542b\u975e\u6cd5\u8def\u5f84\u6761\u76ee: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(target, new FileAttribute[0]);
                } else {
                    Files.createDirectories(target.getParent(), new FileAttribute[0]);
                    Files.copy(zis, target, StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        }
    }

    private String zipFileName(Path zipFile) {
        String fileName = zipFile.getFileName().toString();
        int dot = fileName.lastIndexOf(46);
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    public void removeSkill(String name, String cwd) {
        IExtensionManagementPort.ExtensionSkillSummary target = this.listSkills(cwd).stream().filter(s -> s.name().equals(name)).findFirst().orElseThrow(() -> new IllegalArgumentException("\u6280\u80fd\u4e0d\u5b58\u5728: " + name));
        if (!target.removable()) {
            throw new IllegalArgumentException("\u8be5\u6280\u80fd\u6765\u6e90\u4e3a " + target.source() + "\uff0c\u4ec5\u5141\u8bb8\u5220\u9664\u7528\u6237\u76ee\u5f55\u4e0b\u5b89\u88c5\u7684\u6280\u80fd");
        }
        String path = target.path();
        if (path != null && !path.isBlank()) {
            Path skillPath = Path.of(path, new String[0]);
            if (Files.isDirectory(skillPath, new LinkOption[0])) {
                try {
                    ExtensionConfigStore.deleteRecursively(skillPath);
                }
                catch (IOException e) {
                    throw new IllegalStateException("\u5220\u9664\u6280\u80fd\u76ee\u5f55\u5931\u8d25: " + e.getMessage(), e);
                }
            }
            try {
                Files.deleteIfExists(skillPath);
            }
            catch (IOException e) {
                throw new IllegalStateException("\u5220\u9664\u6280\u80fd\u6587\u4ef6\u5931\u8d25: " + e.getMessage(), e);
            }
        }
        this.store.update(config -> config.disabledSkills.remove(name));
        this.applyDisabledSkills();
        log.info("[extension] skill removed: {}", (Object)name);
    }

    public void setSkillEnabled(String name, boolean enabled) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("\u6280\u80fd\u540d\u4e0d\u80fd\u4e3a\u7a7a");
        }
        this.store.update(config -> {
            config.disabledSkills.remove(name);
            if (!enabled) {
                config.disabledSkills.add(name);
            }
        });
        this.applyDisabledSkills();
        log.info("[extension] skill {} -> {}", (Object)name, (Object)(enabled ? "enabled" : "disabled"));
    }

    private Path installRoot() throws IOException {
        Path root = this.skillProviderPort.dshHome().resolve("skills");
        Files.createDirectories(root, new FileAttribute[0]);
        return root;
    }

    private Path locateSkillDir(Path repo, String subdir) throws IOException {
        if (subdir != null && !subdir.isBlank()) {
            Path candidate = repo.resolve(subdir.trim()).normalize();
            if (Files.isRegularFile(candidate.resolve("SKILL.md"), new LinkOption[0])) {
                return candidate;
            }
            return null;
        }
        if (Files.isRegularFile(repo.resolve("SKILL.md"), new LinkOption[0])) {
            return repo;
        }
        try (Stream<Path> stream = Files.walk(repo, 2, new FileVisitOption[0])) {
            return stream.filter(p -> Files.isRegularFile(p.resolve("SKILL.md"), new LinkOption[0]))
                    .filter(p -> !p.equals(repo))
                    .min(Comparator.comparing(Path::toString))
                    .orElse(null);
        }
        catch (IOException e) {
            return null;
        }
    }

    private String resolveSkillName(String requested, Path skillDir, Path repo, String gitUrl) {
        if (requested != null && !requested.isBlank()) {
            return this.normalizeName(requested, null);
        }
        String frontmatterName = this.readFrontmatterName(skillDir.resolve("SKILL.md"));
        if (frontmatterName != null && !frontmatterName.isBlank()) {
            return this.normalizeName(frontmatterName, null);
        }
        boolean atRepoRoot = repo.equals(skillDir);
        String fallback = atRepoRoot ? this.repoNameFromUrl(gitUrl) : skillDir.getFileName().toString();
        return this.normalizeName(fallback, "installed-skill");
    }

    private String readFrontmatterName(Path file) {
        try {
            String line;
            List<String> lines = Files.readAllLines(file);
            if (lines.isEmpty() || !"---".equals(lines.get(0).trim())) {
                return null;
            }
            for (int i = 1; i < Math.min(lines.size(), 20) && !"---".equals(line = lines.get(i).trim()); ++i) {
                if (!line.startsWith("name:")) continue;
                return line.substring("name:".length()).trim();
            }
            return null;
        }
        catch (IOException e) {
            return null;
        }
    }

    private String repoNameFromUrl(String gitUrl) {
        int slash;
        String trimmed = gitUrl.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        if (trimmed.toLowerCase(Locale.ROOT).endsWith(".git")) {
            trimmed = trimmed.substring(0, trimmed.length() - 4);
        }
        return (slash = Math.max(trimmed.lastIndexOf(47), trimmed.lastIndexOf(58))) >= 0 ? trimmed.substring(slash + 1) : trimmed;
    }

    private String normalizeName(String requested, String fallback) {
        String raw = requested != null && !requested.isBlank() ? requested : fallback;
        String cleaned = raw.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
        cleaned = cleaned.replaceAll("^-+|-+$", "");
        return cleaned.isEmpty() ? "installed-skill" : cleaned;
    }

    private void copyDirectory(Path from, Path to) throws IOException {
        Files.createDirectories(to, new FileAttribute[0]);
        try (Stream<Path> stream = Files.walk(from, new FileVisitOption[0]);){
            for (Path source : stream.toList()) {
                Path dest = to.resolve(from.relativize(source).toString());
                if (Files.isDirectory(source, new LinkOption[0])) {
                    Files.createDirectories(dest, new FileAttribute[0]);
                    continue;
                }
                Files.createDirectories(dest.getParent(), new FileAttribute[0]);
                Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    public List<IExtensionManagementPort.ExtensionMcpServer> listMcpServers() {
        LinkedHashMap<String, IExtensionManagementPort.ExtensionMcpServer> merged = new LinkedHashMap<String, IExtensionManagementPort.ExtensionMcpServer>();
        for (HarnessExtensionsProperties.McpServer preset : this.properties.getMcp().getServers()) {
            if (preset.getName() == null || preset.getName().isBlank()) continue;
            merged.put(preset.getName(), this.toView(preset, "preset"));
        }
        for (ExtensionConfigStore.StoredMcpServer custom : this.store.snapshot().mcpServers) {
            if (custom.name() == null || custom.name().isBlank()) continue;
            merged.put(custom.name(), this.toView(custom, "custom"));
        }
        return new ArrayList<IExtensionManagementPort.ExtensionMcpServer>(merged.values());
    }

    public IExtensionManagementPort.ExtensionMcpApplyResult upsertMcpServer(IExtensionManagementPort.ExtensionMcpServer server) {
        this.validateServer(server);
        IExtensionManagementPort.ExtensionMcpApplyResult probe = this.testMcpServer(server);
        if (!probe.success()) {
            return probe;
        }
        ExtensionConfigStore.StoredMcpServer stored = new ExtensionConfigStore.StoredMcpServer(server.name(), this.transport(server), server.command(), ExtensionConfigStore.copyList(server.args()), ExtensionConfigStore.copyMap(server.env()), server.cwd(), server.url(), ExtensionConfigStore.copyMap(server.headers()));
        this.store.update(config -> {
            config.mcpServers.removeIf(s -> s.name() != null && s.name().equals(server.name()));
            config.mcpServers.add(stored);
        });
        this.mcpRuntime.disconnect(server.name());
        try {
            int count = this.mcpRuntime.connect(this.toConfig(stored));
            List tools = probe.tools();
            return new IExtensionManagementPort.ExtensionMcpApplyResult(true, server.name(), count, tools, "\u5df2\u8fde\u63a5\u5e76\u6ce8\u518c " + count + " \u4e2a\u5de5\u5177");
        }
        catch (Exception e) {
            return new IExtensionManagementPort.ExtensionMcpApplyResult(false, server.name(), 0, List.of(), "\u914d\u7f6e\u5df2\u4fdd\u5b58\uff0c\u4f46\u8fde\u63a5\u5931\u8d25: " + e.getMessage());
        }
    }

    public void removeMcpServer(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name \u4e0d\u80fd\u4e3a\u7a7a");
        }
        this.store.update(config -> config.mcpServers.removeIf(s -> s.name() != null && s.name().equals(name)));
        this.mcpRuntime.disconnect(name);
        log.info("[extension] mcp server removed: {}", (Object)name);
    }

    public IExtensionManagementPort.ExtensionMcpApplyResult testMcpServer(IExtensionManagementPort.ExtensionMcpServer server) {
        this.validateServer(server);
        McpRuntime.ExtensionMcpProbe probe = this.mcpRuntime.probe(this.toProbeConfig(server));
        if (probe.success()) {
            return new IExtensionManagementPort.ExtensionMcpApplyResult(true, server.name(), probe.tools().size(), probe.tools(), "\u8fde\u63a5\u6210\u529f\uff0c\u53d1\u73b0 " + probe.tools().size() + " \u4e2a\u5de5\u5177");
        }
        return new IExtensionManagementPort.ExtensionMcpApplyResult(false, server.name(), 0, List.of(), "\u8fde\u63a5\u5931\u8d25: " + probe.message());
    }

    private void validateServer(IExtensionManagementPort.ExtensionMcpServer server) {
        if (server == null || server.name() == null || server.name().isBlank()) {
            throw new IllegalArgumentException("MCP server name \u4e0d\u80fd\u4e3a\u7a7a");
        }
        String t = this.transport(server);
        if (!Set.of("stdio", "sse", "streamable-http").contains(t)) {
            throw new IllegalArgumentException("\u4e0d\u652f\u6301\u7684 transport: " + t + "\uff08\u53ef\u9009 stdio / sse / streamable-http\uff09");
        }
        if ("stdio".equals(t)) {
            if (server.command() == null || server.command().isBlank()) {
                throw new IllegalArgumentException("stdio \u4f20\u8f93\u9700\u8981\u63d0\u4f9b command");
            }
        } else if (server.url() == null || server.url().isBlank()) {
            throw new IllegalArgumentException(t + " \u4f20\u8f93\u9700\u8981\u63d0\u4f9b url");
        }
    }

    private String transport(IExtensionManagementPort.ExtensionMcpServer server) {
        return server.transport() == null || server.transport().isBlank() ? "stdio" : server.transport().trim();
    }

    private McpServerConfig toConfig(ExtensionConfigStore.StoredMcpServer s) {
        return new McpServerConfig(s.name(), s.command(), ExtensionConfigStore.copyList(s.args()), ExtensionConfigStore.copyMap(s.env()), s.cwd(), s.transport(), s.url(), ExtensionConfigStore.copyMap(s.headers()));
    }

    private McpServerConfig toProbeConfig(IExtensionManagementPort.ExtensionMcpServer s) {
        return new McpServerConfig(s.name(), s.command(), ExtensionConfigStore.copyList(s.args()), ExtensionConfigStore.copyMap(s.env()), s.cwd(), this.transport(s), s.url(), ExtensionConfigStore.copyMap(s.headers()));
    }

    private IExtensionManagementPort.ExtensionMcpServer toView(HarnessExtensionsProperties.McpServer s, String source) {
        return new IExtensionManagementPort.ExtensionMcpServer(s.getName(), s.getTransport(), s.getCommand(), s.getArgs(), s.getEnv(), s.getCwd(), s.getUrl(), s.getHeaders(), source);
    }

    private IExtensionManagementPort.ExtensionMcpServer toView(ExtensionConfigStore.StoredMcpServer s, String source) {
        return new IExtensionManagementPort.ExtensionMcpServer(s.name(), s.transport(), s.command(), s.args(), s.env(), s.cwd(), s.url(), s.headers(), source);
    }

    public IExtensionManagementPort.ExtensionCliConfig getCliConfig() {
        Map<String, String> cli = this.store.snapshot().cli;
        return new IExtensionManagementPort.ExtensionCliConfig(cli.getOrDefault("acpCommand", this.ymlAcpCommand), cli.getOrDefault("acpSubagentCommand", this.ymlAcpSubagentCommand), cli.getOrDefault("codexCommand", this.ymlCodexCommand), cli.getOrDefault("claudeCommand", this.ymlClaudeCommand), "");
    }

    public IExtensionManagementPort.ExtensionCliConfig setCliConfig(IExtensionManagementPort.ExtensionCliConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("\u914d\u7f6e\u4e0d\u80fd\u4e3a\u7a7a");
        }
        IExtensionManagementPort.ExtensionCliConfig effective = new IExtensionManagementPort.ExtensionCliConfig(config.acpCommand(), config.acpSubagentCommand(), config.codexCommand(), config.claudeCommand(), "");
        this.store.update(stored -> {
            stored.cli.put("acpCommand", effective.acpCommand());
            stored.cli.put("acpSubagentCommand", effective.acpSubagentCommand());
            stored.cli.put("codexCommand", effective.codexCommand());
            stored.cli.put("claudeCommand", effective.claudeCommand());
        });
        ArrayList<String> notices = new ArrayList<String>();
        this.applyClaude(effective.claudeCommand(), notices);
        this.applyCodex(effective.codexCommand(), notices);
        this.applyAcp(effective, notices);
        String notice = String.join((CharSequence)"\uff1b", notices);
        return new IExtensionManagementPort.ExtensionCliConfig(effective.acpCommand(), effective.acpSubagentCommand(), effective.codexCommand(), effective.claudeCommand(), notice);
    }

    private void applyClaude(String command, List<String> notices) {
        String cmd;
        ClaudeCodeSubagentProvider provider = (ClaudeCodeSubagentProvider)this.claudeProvider.getIfAvailable();
        String string = cmd = command.isBlank() ? this.ymlClaudeCommand : command;
        if (provider != null) {
            provider.setCommand(cmd);
            this.ensureRegistered(provider);
            notices.add("claude-code \u5b50\u667a\u80fd\u4f53\u547d\u4ee4\u5df2\u70ed\u66f4\u65b0\u4e3a " + cmd);
        }
    }

    private void applyCodex(String command, List<String> notices) {
        String cmd;
        CodexSubagentProvider provider = (CodexSubagentProvider)this.codexProvider.getIfAvailable();
        String string = cmd = command.isBlank() ? this.ymlCodexCommand : command;
        if (provider != null) {
            provider.setCommand(cmd);
            this.ensureRegistered(provider);
            notices.add("codex \u5b50\u667a\u80fd\u4f53\u547d\u4ee4\u5df2\u70ed\u66f4\u65b0\u4e3a " + cmd);
        }
    }

    private void applyAcp(IExtensionManagementPort.ExtensionCliConfig config, List<String> notices) {
        AcpSubagentProvider provider = (AcpSubagentProvider)this.acpProvider.getIfAvailable();
        if (provider != null && !config.acpSubagentCommand().isBlank()) {
            provider.setCommand(config.acpSubagentCommand());
            this.ensureRegistered(provider);
            notices.add("ACP \u5b50\u667a\u80fd\u4f53\u547d\u4ee4\u5df2\u70ed\u66f4\u65b0\u4e3a " + config.acpSubagentCommand());
        } else if (!config.acpSubagentCommand().isBlank()) {
            notices.add("ACP \u4e3b\u901a\u9053\u547d\u4ee4\u5c06\u5728\u670d\u52a1\u91cd\u542f\u540e\u751f\u6548\uff08\u5f53\u524d\u4f1a\u8bdd\u4ecd\u53ef\u7528 claude-code/codex \u5b50\u667a\u80fd\u4f53\uff09");
        }
    }

    private void ensureRegistered(SubagentProvider provider) {
        try {
            this.subagentRegistry.resolve(provider.name());
        }
        catch (IllegalArgumentException e) {
            this.subagentRegistry.register(provider);
        }
    }

    public Set<String> connectedMcpServers() {
        return new TreeSet<String>(this.mcpRuntime.status().keySet());
    }
}

