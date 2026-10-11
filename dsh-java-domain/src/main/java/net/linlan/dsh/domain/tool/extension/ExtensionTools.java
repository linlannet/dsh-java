package net.linlan.dsh.domain.tool.extension;

import net.linlan.dsh.domain.extension.IExtensionManagementPort;
import net.linlan.dsh.domain.model.entity.ToolDefinition;
import net.linlan.dsh.domain.model.entity.ToolExecutionResult;
import net.linlan.dsh.domain.model.entity.ToolRunContext;
import net.linlan.dsh.domain.model.valobj.ContentBlock;
import net.linlan.dsh.domain.model.valobj.TextBlock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class ExtensionTools {
    private ExtensionTools() {
    }

    static String str(Map<String, Object> args, String key) {
        Object value = args.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    static Map<String, Object> asMap(Object args) {
        return args instanceof Map ? (Map)args : Map.of();
    }

    static CompletableFuture<ToolExecutionResult> ok(String text) {
        List<ContentBlock> content = List.of(new TextBlock(text));
        return CompletableFuture.completedFuture(ToolExecutionResult.ok(content));
    }

    private static List<String> stringList(Object raw) {
        ArrayList<String> list;
        block3: {
            block2: {
                list = new ArrayList<String>();
                if (!(raw instanceof List)) break block2;
                List l = (List)raw;
                for (Object item : l) {
                    if (item == null) continue;
                    list.add(String.valueOf(item));
                }
                break block3;
            }
            if (!(raw instanceof Map)) break block3;
            Map m = (Map)raw;
            for (Object value : m.values()) {
                if (value == null) continue;
                list.add(String.valueOf(value));
            }
        }
        return list;
    }

    private static Map<String, String> stringMap(Object raw) {
        LinkedHashMap<String, String> map = new LinkedHashMap<String, String>();
        if (raw instanceof Map) {
            Map<?, ?> m = (Map<?, ?>) raw;
            for (Map.Entry<?, ?> entry : m.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) continue;
                map.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue()));
            }
        }
        return map;
    }

    public static class McpRemoveTool
    implements ToolDefinition {
        public static final String NAME = "extension_mcp_remove";
        private static final String DESCRIPTION = "Remove a configured MCP server: disconnect it and unregister its tools.";
        private final IExtensionManagementPort port;

        public McpRemoveTool(IExtensionManagementPort port) {
            this.port = port;
        }

        public String name() {
            return NAME;
        }

        public String description() {
            return DESCRIPTION;
        }

        public Map<String, Object> parameters() {
            return Map.of("type", "object", "additionalProperties", false, "properties", Map.of("name", Map.of("type", "string", "description", "Server name to remove.")), "required", List.of("name"));
        }

        public CompletableFuture<ToolExecutionResult> execute(Object args, ToolRunContext ctx) {
            String name = ExtensionTools.str(ExtensionTools.asMap(args), "name");
            if (name.isEmpty()) {
                return CompletableFuture.completedFuture(ToolExecutionResult.fail((String)"name is required", (String)"MISSING_PARAM"));
            }
            try {
                this.port.removeMcpServer(name);
                return ExtensionTools.ok("MCP server " + name + " \u5df2\u79fb\u9664\u5e76\u65ad\u5f00\u3002");
            }
            catch (Exception e) {
                return CompletableFuture.completedFuture(ToolExecutionResult.fail((String)e.getMessage(), (String)"OPERATION_FAILED"));
            }
        }

        public boolean isConcurrencySafe(Object args) {
            return false;
        }
    }

    public static class McpConfigureTool
    implements ToolDefinition {
        public static final String NAME = "extension_mcp_configure";
        private static final String DESCRIPTION = "Add or update an MCP server, test its connection, and register its tools as mcp__<server>__<tool>. transport=stdio needs command(+args); sse / streamable-http need url (headers/env optional). Use test=true to only validate without saving.";
        private final IExtensionManagementPort port;

        public McpConfigureTool(IExtensionManagementPort port) {
            this.port = port;
        }

        public String name() {
            return NAME;
        }

        public String description() {
            return DESCRIPTION;
        }

        public Map<String, Object> parameters() {
            return Map.of("type", "object", "additionalProperties", false, "properties", Map.of("name", Map.of("type", "string", "description", "Unique server name."), "transport", Map.of("type", "string", "description", "stdio | sse | streamable-http. Default stdio.", "enum", List.of("stdio", "sse", "streamable-http")), "command", Map.of("type", "string", "description", "stdio: executable, e.g. npx"), "args", Map.of("type", "array", "items", Map.of("type", "string"), "description", "stdio: command arguments"), "url", Map.of("type", "string", "description", "sse/streamable-http: server URL"), "env", Map.of("type", "object", "additionalProperties", Map.of("type", "string"), "description", "stdio: extra environment variables, e.g. API keys"), "headers", Map.of("type", "object", "additionalProperties", Map.of("type", "string"), "description", "sse/streamable-http: HTTP headers, e.g. Authorization"), "test", Map.of("type", "boolean", "description", "true = only test connection, do not save")), "required", List.of("name", "transport"));
        }

        public CompletableFuture<ToolExecutionResult> execute(Object args, ToolRunContext ctx) {
            String transport;
            Map<String, Object> map = ExtensionTools.asMap(args);
            String name = ExtensionTools.str(map, "name");
            String string = transport = ExtensionTools.str(map, "transport").isEmpty() ? "stdio" : ExtensionTools.str(map, "transport");
            if (name.isEmpty()) {
                return CompletableFuture.completedFuture(ToolExecutionResult.fail((String)"name is required", (String)"MISSING_PARAM"));
            }
            IExtensionManagementPort.ExtensionMcpServer server = new IExtensionManagementPort.ExtensionMcpServer(name, transport, ExtensionTools.str(map, "command"), ExtensionTools.stringList(map.get("args")), ExtensionTools.stringMap(map.get("env")), "", ExtensionTools.str(map, "url"), ExtensionTools.stringMap(map.get("headers")), "custom");
            try {
                String head;
                boolean testOnly = Boolean.parseBoolean(ExtensionTools.str(map, "test")) || Boolean.TRUE.equals(map.get("test"));
                IExtensionManagementPort.ExtensionMcpApplyResult result = testOnly ? this.port.testMcpServer(server) : this.port.upsertMcpServer(server);
                String string2 = head = testOnly ? "[\u6d4b\u8bd5] " : "";
                if (result.success()) {
                    StringBuilder sb = new StringBuilder(head).append(result.message());
                    if (!result.tools().isEmpty()) {
                        sb.append("\n\u5de5\u5177: ").append(String.join((CharSequence)", ", result.tools()));
                    }
                    return ExtensionTools.ok(sb.toString());
                }
                return CompletableFuture.completedFuture(ToolExecutionResult.fail((String)result.message(), (String)"MCP_CONNECT_FAILED"));
            }
            catch (Exception e) {
                return CompletableFuture.completedFuture(ToolExecutionResult.fail((String)e.getMessage(), (String)"MCP_CONFIG_FAILED"));
            }
        }

        public boolean isConcurrencySafe(Object args) {
            return false;
        }
    }

    public static class McpListTool
    implements ToolDefinition {
        public static final String NAME = "extension_mcp_list";
        private static final String DESCRIPTION = "List configured MCP servers (name, transport, endpoint/command, status, registered tool count).";
        private final IExtensionManagementPort port;

        public McpListTool(IExtensionManagementPort port) {
            this.port = port;
        }

        public String name() {
            return NAME;
        }

        public String description() {
            return DESCRIPTION;
        }

        public Map<String, Object> parameters() {
            return Map.of("type", "object", "additionalProperties", false, "properties", Map.of());
        }

        public CompletableFuture<ToolExecutionResult> execute(Object args, ToolRunContext ctx) {
            List<IExtensionManagementPort.ExtensionMcpServer> servers = this.port.listMcpServers();
            if (servers.isEmpty()) {
                return ExtensionTools.ok("\u5f53\u524d\u6ca1\u6709\u914d\u7f6e MCP server\u3002\u53ef\u7528 extension_mcp_configure \u6dfb\u52a0\u3002");
            }
            StringBuilder sb = new StringBuilder("MCP servers\uff1a\n");
            for (IExtensionManagementPort.ExtensionMcpServer s : servers) {
                sb.append("- ").append(s.name()).append(" transport=").append(s.transport()).append(" source=").append(s.presetSource());
                if ("stdio".equals(s.transport())) {
                    sb.append(" command=").append(s.command());
                } else {
                    sb.append(" url=").append(s.url());
                }
                sb.append('\n');
            }
            return ExtensionTools.ok(sb.toString());
        }

        public boolean isConcurrencySafe(Object args) {
            return true;
        }
    }

    public static class SkillManageTool
    implements ToolDefinition {
        public static final String NAME = "extension_skill_manage";
        private static final String DESCRIPTION = "Enable, disable, or remove an installed skill. action=enable|disable|remove. Disabled skills are hidden from the skill tool.";
        private final IExtensionManagementPort port;

        public SkillManageTool(IExtensionManagementPort port) {
            this.port = port;
        }

        public String name() {
            return NAME;
        }

        public String description() {
            return DESCRIPTION;
        }

        public Map<String, Object> parameters() {
            return Map.of("type", "object", "additionalProperties", false, "properties", Map.of("name", Map.of("type", "string", "description", "Skill name (kebab-case)."), "action", Map.of("type", "string", "description", "One of: enable, disable, remove.", "enum", List.of("enable", "disable", "remove"))), "required", List.of("name", "action"));
        }

        public CompletableFuture<ToolExecutionResult> execute(Object args, ToolRunContext ctx) {
            Map<String, Object> map = ExtensionTools.asMap(args);
            String name = ExtensionTools.str(map, "name");
            String action = ExtensionTools.str(map, "action").toLowerCase();
            if (name.isEmpty()) {
                return CompletableFuture.completedFuture(ToolExecutionResult.fail((String)"name is required", (String)"MISSING_PARAM"));
            }
            try {
                switch (action) {
                    case "enable": {
                        this.port.setSkillEnabled(name, true);
                        return ExtensionTools.ok("\u6280\u80fd " + name + " \u5df2\u542f\u7528\u3002");
                    }
                    case "disable": {
                        this.port.setSkillEnabled(name, false);
                        return ExtensionTools.ok("\u6280\u80fd " + name + " \u5df2\u505c\u7528\u3002");
                    }
                    case "remove": {
                        this.port.removeSkill(name, null);
                        return ExtensionTools.ok("\u6280\u80fd " + name + " \u5df2\u5220\u9664\u3002");
                    }
                }
                return CompletableFuture.completedFuture(ToolExecutionResult.fail((String)"action \u5fc5\u987b\u662f enable/disable/remove", (String)"INVALID_PARAM"));
            }
            catch (Exception e) {
                return CompletableFuture.completedFuture(ToolExecutionResult.fail((String)e.getMessage(), (String)"OPERATION_FAILED"));
            }
        }

        public boolean isConcurrencySafe(Object args) {
            return false;
        }
    }

    public static class SkillInstallTool
    implements ToolDefinition {
        public static final String NAME = "extension_skill_install";
        private static final String DESCRIPTION = "Install a skill from a Git repository: clone the repo, locate SKILL.md, and copy the skill into the user skills directory. Use when the user asks to install/add a skill, e.g. \"\u5e2e\u6211\u88c5\u4e00\u4e2a xxx \u6280\u80fd\". Returns the installed skill name; verify with extension_skill_list.";
        private final IExtensionManagementPort port;

        public SkillInstallTool(IExtensionManagementPort port) {
            this.port = port;
        }

        public String name() {
            return NAME;
        }

        public String description() {
            return DESCRIPTION;
        }

        public Map<String, Object> parameters() {
            return Map.of("type", "object", "additionalProperties", false, "properties", Map.of("gitUrl", Map.of("type", "string", "description", "Git repository URL (https or ssh), e.g. https://github.com/user/awesome-skills.git"), "subdir", Map.of("type", "string", "description", "Optional: path inside the repo to the skill directory containing SKILL.md. Omit to auto-search."), "name", Map.of("type", "string", "description", "Optional: skill directory name (kebab-case). Defaults to repo/skill directory name.")), "required", List.of("gitUrl"));
        }

        public CompletableFuture<ToolExecutionResult> execute(Object args, ToolRunContext ctx) {
            Map<String, Object> map = ExtensionTools.asMap(args);
            String gitUrl = ExtensionTools.str(map, "gitUrl");
            if (gitUrl.isEmpty()) {
                return CompletableFuture.completedFuture(ToolExecutionResult.fail((String)"gitUrl is required", (String)"MISSING_PARAM"));
            }
            IExtensionManagementPort.ExtensionSkillInstallResult result = this.port.installSkillFromGit(gitUrl, ExtensionTools.str(map, "subdir"), ExtensionTools.str(map, "name"));
            if (result.success()) {
                return ExtensionTools.ok(result.message() + "\n\u8def\u5f84: " + result.path() + "\n\u6280\u80fd\u5df2\u53ef\u7528\uff08skill \u5de5\u5177\u6309\u540d\u79f0\u52a0\u8f7d\uff0c\u6216\u91cd\u542f\u4f1a\u8bdd\u540e\u81ea\u52a8\u8fdb\u5165\u6280\u80fd\u76ee\u5f55\uff09\u3002");
            }
            return CompletableFuture.completedFuture(ToolExecutionResult.fail((String)result.message(), (String)"INSTALL_FAILED"));
        }

        public boolean isConcurrencySafe(Object args) {
            return false;
        }
    }

    public static class SkillListTool
    implements ToolDefinition {
        public static final String NAME = "extension_skill_list";
        private static final String DESCRIPTION = "List all installed skills (name, description, source, enabled). Call this before installing or toggling a skill to see what already exists.";
        private final IExtensionManagementPort port;

        public SkillListTool(IExtensionManagementPort port) {
            this.port = port;
        }

        public String name() {
            return NAME;
        }

        public String description() {
            return DESCRIPTION;
        }

        public Map<String, Object> parameters() {
            return Map.of("type", "object", "additionalProperties", false, "properties", Map.of());
        }

        public CompletableFuture<ToolExecutionResult> execute(Object args, ToolRunContext ctx) {
            List<IExtensionManagementPort.ExtensionSkillSummary> skills = this.port.listSkills(null);
            if (skills.isEmpty()) {
                return ExtensionTools.ok("\u5f53\u524d\u6ca1\u6709\u5df2\u5b89\u88c5\u7684\u6280\u80fd\u3002\u53ef\u7528 extension_skill_install \u4ece Git \u4ed3\u5e93\u5b89\u88c5\u3002");
            }
            StringBuilder sb = new StringBuilder("\u5df2\u5b89\u88c5\u6280\u80fd\uff1a\n");
            for (IExtensionManagementPort.ExtensionSkillSummary s : skills) {
                sb.append("- ").append(s.name()).append(" [").append(s.enabled() ? "\u542f\u7528" : "\u505c\u7528").append("]").append(" \u6765\u6e90:").append(s.source()).append((String)(s.description() == null || s.description().isBlank() ? "" : " \u00b7 " + s.description())).append('\n');
            }
            return ExtensionTools.ok(sb.toString());
        }

        public boolean isConcurrencySafe(Object args) {
            return true;
        }
    }
}

