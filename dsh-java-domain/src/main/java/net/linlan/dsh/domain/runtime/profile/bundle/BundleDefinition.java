package net.linlan.dsh.domain.runtime.profile.bundle;

import java.util.List;
import java.util.Map;

/**
 * BundleDefinition 描述一组插件与配置的预定义装配单元。
 *
 * @param name bundle 名称
 * @param plugins 插件标识列表
 * @param config 配置键值
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
public record BundleDefinition(
        String name,
        List<String> plugins,
        Map<String, String> config
) {

    public static final BundleDefinition BASE = new BundleDefinition(
            "base",
            List.of("core", "fs", "shell"),
            Map.of()
    );

    public static final BundleDefinition HEADLESS = new BundleDefinition(
            "headless",
            List.of("core", "fs", "shell", "terminal", "tool"),
            Map.of("ui.mode", "headless")
    );

    public static final BundleDefinition SDK_APP = new BundleDefinition(
            "sdk-app",
            List.of("core", "fs", "shell", "terminal", "tool", "sdk", "plugin"),
            Map.of("ui.mode", "sdk", "runtime.profile", "sdk-app")
    );

    public static final BundleDefinition SDK_MINIMAL = new BundleDefinition(
            "sdk-minimal",
            List.of("core", "fs", "sdk"),
            Map.of("ui.mode", "sdk", "runtime.profile", "sdk-minimal")
    );

    public static final BundleDefinition ACP_APP = new BundleDefinition(
            "acp-app",
            List.of("core", "fs", "shell", "tool", "acp", "plugin"),
            Map.of("ui.mode", "acp", "runtime.profile", "acp-app")
    );

    public static final BundleDefinition WEB_APP = new BundleDefinition(
            "web-app",
            List.of("core", "fs", "shell", "tool", "web", "plugin"),
            Map.of("ui.mode", "web", "runtime.profile", "web-app")
    );

    public BundleDefinition {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("bundle name required");
        }
        plugins = plugins == null ? List.of() : List.copyOf(plugins);
        config = config == null ? Map.of() : Map.copyOf(config);
    }
}
