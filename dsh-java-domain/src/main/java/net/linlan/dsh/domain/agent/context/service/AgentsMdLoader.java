package net.linlan.dsh.domain.agent.context.service;

import net.linlan.dsh.domain.agent.context.model.valobj.Instructions;
import net.linlan.dsh.domain.tool.fs.adapter.port.IFsPort;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AgentsMdLoader 从工作区层级加载 AGENTS.md 并合并指令。
 * <p>从工作区根目录到当前目录逐层查找 AGENTS.md，子级覆盖父级同键指令。
 */
public final class AgentsMdLoader {

    private static final String AGENTS_MD = "AGENTS.md";
    private static final String SEPARATOR = "/";

    private final IFsPort fsPort;

    public AgentsMdLoader(IFsPort fsPort) {
        if (fsPort == null) {
            throw new IllegalArgumentException("fsPort must not be null");
        }
        this.fsPort = fsPort;
    }

    /**
     * 从工作区层级加载 AGENTS.md。
     *
     * @param workspaceRoot 工作区根路径
     * @return 合并后的指令集（无文件时返回 empty）
     */
    public Instructions load(String workspaceRoot) {
        if (workspaceRoot == null || workspaceRoot.isBlank()) {
            return Instructions.empty(AGENTS_MD);
        }
        List<Instructions> layered = loadLayered(workspaceRoot);
        return merge(layered);
    }

    /**
     * 从工作区根到 cwd 逐层加载 AGENTS.md。
     *
     * @param cwd 当前工作目录
     * @return 层级指令列表（从根到当前目录）
     */
    private List<Instructions> loadLayered(String cwd) {
        List<Instructions> result = new ArrayList<>();
        List<String> paths = splitPathComponents(cwd);

        String prefix = cwd.startsWith("/") ? "/" : "";
        StringBuilder current = new StringBuilder(prefix);
        for (int i = 0; i < paths.size(); i++) {
            if (current.length() > 0 && !current.toString().endsWith("/")) {
                current.append(SEPARATOR);
            }
            current.append(paths.get(i));
            String agentsPath = current.toString() + SEPARATOR + AGENTS_MD;
            if (fsPort.exists(agentsPath)) {
                String content = fsPort.read(agentsPath, 0, Integer.MAX_VALUE);
                result.add(new Instructions(agentsPath, content, parseDirectives(content)));
            }
        }
        return result;
    }

    /**
     * 合并层级指令，子级覆盖父级同键。
     *
     * @param layered 层级指令列表（从根到当前目录）
     * @return 合并后的指令集
     */
    public Instructions merge(List<Instructions> layered) {
        if (layered == null || layered.isEmpty()) {
            return Instructions.empty(AGENTS_MD);
        }
        Instructions merged = layered.get(0);
        for (int i = 1; i < layered.size(); i++) {
            merged = merged.merge(layered.get(i));
        }
        return merged;
    }

    /**
     * 解析 AGENTS.md 内容为指令键值对。
     * <p>支持简单格式：{@code key: value} 或 {@code key=value}。
     */
    private Map<String, String> parseDirectives(String content) {
        Map<String, String> directives = new LinkedHashMap<>();
        if (content == null || content.isBlank()) {
            return directives;
        }
        for (String line : content.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("//")) {
                continue;
            }
            int sep = trimmed.indexOf(':');
            if (sep < 0) {
                sep = trimmed.indexOf('=');
            }
            if (sep > 0) {
                String key = trimmed.substring(0, sep).trim();
                String value = trimmed.substring(sep + 1).trim();
                if (!key.isEmpty()) {
                    directives.put(key, value);
                }
            }
        }
        return directives;
    }

    /**
     * 将路径拆分为组件列表。
     */
    private List<String> splitPathComponents(String path) {
        List<String> components = new ArrayList<>();
        String normalized = path.replace("\\", SEPARATOR);
        for (String part : normalized.split(SEPARATOR)) {
            if (!part.isEmpty() && !part.equals(".")) {
                components.add(part);
            }
        }
        return components;
    }
}
