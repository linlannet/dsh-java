package net.linlan.dsh.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Unified extension configuration for skills, MCP servers, and plugins.
 */
@ConfigurationProperties(prefix = "harness.extensions")
public class HarnessExtensionsProperties {

    private Skills skills = new Skills();
    private Mcp mcp = new Mcp();
    private Plugins plugins = new Plugins();

    public Skills getSkills() {
        return skills;
    }

    public void setSkills(Skills skills) {
        this.skills = skills != null ? skills : new Skills();
    }

    public Mcp getMcp() {
        return mcp;
    }

    public void setMcp(Mcp mcp) {
        this.mcp = mcp != null ? mcp : new Mcp();
    }

    public Plugins getPlugins() {
        return plugins;
    }

    public void setPlugins(Plugins plugins) {
        this.plugins = plugins != null ? plugins : new Plugins();
    }

    public static class Skills {
        private boolean enabled = true;
        private boolean projectEnabled = true;
        private String home = "";
        private String bundledDir = "";
        private List<String> roots = new ArrayList<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isProjectEnabled() {
            return projectEnabled;
        }

        public void setProjectEnabled(boolean projectEnabled) {
            this.projectEnabled = projectEnabled;
        }

        public String getHome() {
            return home;
        }

        public void setHome(String home) {
            this.home = home != null ? home : "";
        }

        public String getBundledDir() {
            return bundledDir;
        }

        public void setBundledDir(String bundledDir) {
            this.bundledDir = bundledDir != null ? bundledDir : "";
        }

        public List<String> getRoots() {
            return roots;
        }

        public void setRoots(List<String> roots) {
            this.roots = roots != null ? roots : new ArrayList<>();
        }
    }

    public static class Mcp {
        private boolean enabled = true;
        private List<McpServer> servers = new ArrayList<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<McpServer> getServers() {
            return servers;
        }

        public void setServers(List<McpServer> servers) {
            this.servers = servers != null ? servers : new ArrayList<>();
        }
    }

    public static class McpServer {
        private String name;
        private String transport = "stdio";
        private String command;
        private List<String> args = new ArrayList<>();
        private Map<String, String> env = new HashMap<>();
        private String cwd;
        private String url;
        private Map<String, String> headers = new HashMap<>();

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getTransport() {
            return transport;
        }

        public void setTransport(String transport) {
            this.transport = transport != null && !transport.isBlank() ? transport : "stdio";
        }

        public String getCommand() {
            return command;
        }

        public void setCommand(String command) {
            this.command = command;
        }

        public List<String> getArgs() {
            return args;
        }

        public void setArgs(List<String> args) {
            this.args = args != null ? args : new ArrayList<>();
        }

        public Map<String, String> getEnv() {
            return env;
        }

        public void setEnv(Map<String, String> env) {
            this.env = env != null ? env : new HashMap<>();
        }

        public String getCwd() {
            return cwd;
        }

        public void setCwd(String cwd) {
            this.cwd = cwd;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public Map<String, String> getHeaders() {
            return headers;
        }

        public void setHeaders(Map<String, String> headers) {
            this.headers = headers != null ? headers : new HashMap<>();
        }
    }

    public static class Plugins {
        private String installRoot = "./plugins";
        private boolean nodeBridgeEnabled = true;
        private List<PresetPlugin> preset = new ArrayList<>();

        public String getInstallRoot() {
            return installRoot;
        }

        public void setInstallRoot(String installRoot) {
            this.installRoot = installRoot != null ? installRoot : "./plugins";
        }

        public boolean isNodeBridgeEnabled() {
            return nodeBridgeEnabled;
        }

        public void setNodeBridgeEnabled(boolean nodeBridgeEnabled) {
            this.nodeBridgeEnabled = nodeBridgeEnabled;
        }

        public List<PresetPlugin> getPreset() {
            return preset;
        }

        public void setPreset(List<PresetPlugin> preset) {
            this.preset = preset != null ? preset : new ArrayList<>();
        }
    }

    public static class PresetPlugin {
        private String pluginId;
        private String displayName;
        private String pluginVersion;
        private String runtimeType;
        private String sourcePath;
        private String entrypoint;
        private Boolean autoStart;
        private boolean autoEnable = true;

        public String getPluginId() {
            return pluginId;
        }

        public void setPluginId(String pluginId) {
            this.pluginId = pluginId;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public String getPluginVersion() {
            return pluginVersion;
        }

        public void setPluginVersion(String pluginVersion) {
            this.pluginVersion = pluginVersion;
        }

        public String getRuntimeType() {
            return runtimeType;
        }

        public void setRuntimeType(String runtimeType) {
            this.runtimeType = runtimeType;
        }

        public String getSourcePath() {
            return sourcePath;
        }

        public void setSourcePath(String sourcePath) {
            this.sourcePath = sourcePath;
        }

        public String getEntrypoint() {
            return entrypoint;
        }

        public void setEntrypoint(String entrypoint) {
            this.entrypoint = entrypoint;
        }

        public boolean isAutoStart() {
            return autoStart != null ? autoStart : autoEnable;
        }

        public Boolean getAutoStart() {
            return autoStart;
        }

        public void setAutoStart(Boolean autoStart) {
            this.autoStart = autoStart;
        }

        public boolean isAutoEnable() {
            return autoEnable;
        }

        public void setAutoEnable(boolean autoEnable) {
            this.autoEnable = autoEnable;
        }
    }
}
