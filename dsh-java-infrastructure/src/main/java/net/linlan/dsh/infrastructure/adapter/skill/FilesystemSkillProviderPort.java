package net.linlan.dsh.infrastructure.adapter.skill;

import net.linlan.dsh.domain.skill.adapter.port.ISkillProviderPort;
import net.linlan.dsh.domain.skill.model.entity.SkillCandidate;
import net.linlan.dsh.domain.skill.model.entity.SkillDefinition;
import net.linlan.dsh.domain.skill.model.valobj.SkillInvocationPolicy;
import net.linlan.dsh.domain.skill.model.valobj.SkillSource;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Local filesystem skill provider. Discovers directory-bundle and flat Markdown
 * skills from project, custom, and user roots, parses YAML frontmatter.
 *
 * <p>调用流程：领域端口 → 基础设施适配器 → 外部系统。</p>
 * <p>示例：适配器只实现端口契约，业务规则保留在 domain。</p>
 *
 */
public class FilesystemSkillProviderPort implements ISkillProviderPort {

    private static final int PROJECT_DSH_RANK = 100;
    private static final int CUSTOM_RANK = 300;
    private static final int USER_DSH_RANK = 400;
    private static final int BUNDLED_RANK = 600;
    private static final Pattern FRONTMATTER = Pattern.compile("^---\\s*\\n(.*?)\\n---\\s*\\n(.*)$", Pattern.DOTALL);

    private final Path dshHome;
    private final Path bundledDir;
    private final String providerName;
    private final List<Path> customRoots;
    private final boolean enabled;
    private final boolean projectEnabled;
    private volatile Set<String> disabledNames = Set.of();

    /**
     * 处理Filesystem Skill 模型提供方 Port。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param dshHome dsh Home参数。
     * @param bundledDir bundled Dir参数。
     * @return 方法执行结果。
     */
    public FilesystemSkillProviderPort(Path dshHome, Path bundledDir) {
        this(dshHome, bundledDir, "filesystem", List.of(), true, true);
    }

    /**
     * 处理Filesystem Skill 模型提供方 Port。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param dshHome dsh Home参数。
     * @param bundledDir bundled Dir参数。
     * @param providerName 模型提供方 名称参数。
     * @return 方法执行结果。
     */
    public FilesystemSkillProviderPort(Path dshHome, Path bundledDir, String providerName) {
        this(dshHome, bundledDir, providerName, List.of(), true, true);
    }

    public FilesystemSkillProviderPort(
            Path dshHome,
            Path bundledDir,
            String providerName,
            List<Path> customRoots,
            boolean enabled,
            boolean projectEnabled
    ) {
        this.dshHome = dshHome;
        this.bundledDir = bundledDir;
        this.providerName = providerName;
        this.customRoots = customRoots != null ? List.copyOf(customRoots) : List.of();
        this.enabled = enabled;
        this.projectEnabled = projectEnabled;
    }

    /**
     * 设置禁用的技能名集合。
     * @param names 禁用名称集合。
     */
    public void setDisabledNames(Set<String> names) {
        this.disabledNames = names == null ? Set.of() : Set.copyOf(names);
    }

    /**
     * 返回 dshHome。
     * @return dshHome 路径。
     */
    public Path dshHome() {
        return dshHome;
    }

    /**
     * 返回 customRoots。
     * @return 自定义根列表。
     */
    public List<Path> customRoots() {
        return customRoots;
    }

    @Override
    public List<SkillCandidate> discover(String cwd) {
        if (!enabled) return List.of();
        Set<String> disabled = this.disabledNames;
        List<SkillCandidate> candidates = new ArrayList<>();
        // Project skills: <cwd>/.dsh/skills
        if (projectEnabled && cwd != null) {
            Path projectSkills = Path.of(cwd).resolve(".dsh").resolve("skills");
            candidates.addAll(discoverRoot(projectSkills, SkillSource.PROJECT_DSH, PROJECT_DSH_RANK, disabled));
        }
        for (Path customRoot : customRoots) {
            candidates.addAll(discoverRoot(customRoot, SkillSource.CUSTOM, CUSTOM_RANK, disabled));
        }
        // User skills: <dshHome>/skills
        candidates.addAll(discoverRoot(dshHome.resolve("skills"), SkillSource.USER_DSH, USER_DSH_RANK, disabled));
        // Bundled skills
        if (bundledDir != null && Files.isDirectory(bundledDir)) {
            candidates.addAll(discoverRoot(bundledDir, SkillSource.BUNDLED, BUNDLED_RANK, disabled));
        }
    /**
     * 加载。
     */
        return candidates;
    }

    @Override
    public Optional<SkillDefinition> load(SkillCandidate candidate) {
        if (!enabled) return Optional.empty();
        if (candidate.path().isEmpty()) return Optional.empty();
        Path skillFile = Path.of(candidate.path().get());
        try {
            String raw = Files.readString(skillFile);
            ParsedSkill parsed = parseSkill(raw);
            return Optional.of(new SkillDefinition(
                    candidate.name(),
                    parsed.description,
                    Optional.ofNullable(parsed.whenToUse),
                    candidate.invocation(),
                    candidate.source(),
                    candidate.provider(),
                    parsed.content,
                    candidate.path()
            ));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /**
     * 发现 根路径。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param root 根路径。
     * @param source 消息来源。
     * @param rank rank参数。
     * @return 方法执行结果。
     */
    private List<SkillCandidate> discoverRoot(Path root, SkillSource source, int rank, Set<String> disabled) {
        if (!Files.isDirectory(root)) return List.of();
        List<SkillCandidate> result = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
            for (Path entry : stream) {
                String name = entry.getFileName().toString();
                if (disabled.contains(name)) continue;
                // Directory bundle: <name>/SKILL.md
                if (Files.isDirectory(entry)) {
                    Path skillFile = entry.resolve("SKILL.md");
                    if (Files.isRegularFile(skillFile)) {
                        addCandidate(result, name, skillFile, source, rank);
                    }
                } else if (name.endsWith(".md")) {
                    // Flat markdown: <name>.md
                    String skillName = name.substring(0, name.length() - 3);
                    if (disabled.contains(skillName)) continue;
                    addCandidate(result, skillName, entry, source, rank);
                }
            }
        } catch (IOException e) {
            // non-fatal
        }
        return result;
    }

    /**
     * 追加数据到当前集合或目标对象。
     */
    private void addCandidate(List<SkillCandidate> result, String name, Path file,
                              SkillSource source, int rank) {
        try {
            String raw = Files.readString(file);
            ParsedSkill parsed = parseSkill(raw);
            result.add(new SkillCandidate(
                    name, parsed.description, Optional.ofNullable(parsed.whenToUse),
                    SkillInvocationPolicy.both(), source, providerName,
                    Optional.of(file.toAbsolutePath().toString()), rank,
                    file.toAbsolutePath().toString()
            ));
        } catch (IOException e) {
            // skip unreadable
        }
    }

    /**
     * 解析 Skill。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param raw raw参数。
     * @return 方法执行结果。
     */
    private ParsedSkill parseSkill(String raw) {
        String description = "";
        String whenToUse = null;
        String content = raw;
        Matcher m = FRONTMATTER.matcher(raw);
        if (m.matches()) {
            String frontmatter = m.group(1);
            content = m.group(2);
            for (String line : frontmatter.split("\n")) {
                int colon = line.indexOf(':');
                if (colon <= 0) continue;
                String key = line.substring(0, colon).trim();
                String val = line.substring(colon + 1).trim();
                switch (key) {
                    case "description" -> description = val;
                    case "when_to_use" -> whenToUse = val;
                }
            }
        }
        // If no frontmatter, use first line as description
        if (description.isEmpty()) {
            String firstLine = raw.lines().findFirst().orElse("");
            if (firstLine.startsWith("#")) {
                description = firstLine.substring(1).trim();
            } else {
                description = firstLine;
            }
        }
        return new ParsedSkill(description, whenToUse, content);
    }

    /**
     * ParsedSkill 是基础设施适配器，负责外部资源与领域端口对接。
     */
    private record ParsedSkill(String description, String whenToUse, String content) {}
}
