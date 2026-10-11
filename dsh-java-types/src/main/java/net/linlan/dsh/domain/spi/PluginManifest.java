package net.linlan.dsh.domain.spi;

import java.util.Objects;

/**
 * {@code META-INF/plugin.yaml} 清单的解析结果。
 * <p>
 * 清单是嵌入插件 JAR 的轻量 YAML 文件：
 * <pre>{@code
 * id: my-plugin
 * name: My Plugin
 * version: 1.0.0
 * author: Jane Doe
 * description: A sample Java harness plugin
 * entrypoint: com.example.MyPluginEntry
 * }</pre>
 * <p>
 * {@code entrypoint} 是实现 {@link JavaHarnessPlugin} 的全限定类名。该字段可选；
 * 未填写时 Harness 会回退到 {@code META-INF/services} 的 ServiceLoader 发现机制。
 *
 *
 */
public final class PluginManifest {

    private final String id;
    private final String name;
    private final String version;
    private final String author;
    private final String description;
    private final String entrypoint;

    /**
     * 创建插件清单。
     * @param id 插件 ID。
     * @param name 插件名称。
     * @param version 插件版本。
     * @param author 作者。
     * @param description 描述。
     * @param entrypoint 插件入口。
     */
    public PluginManifest(String id, String name, String version, String author, String description, String entrypoint) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.version = Objects.requireNonNull(version, "version");
        this.author = author;
        this.description = description;
        this.entrypoint = entrypoint;
    }

    /**
     * 处理ID。
     * @return 方法执行结果。
     */
    public String id() { return id; }
    /**
     * 处理名称。
     * @return 方法执行结果。
     */
    public String name() { return name; }
    /**
     * 处理版本。
     * @return 方法执行结果。
     */
    public String version() { return version; }
    /**
     * 处理author。
     * @return 方法执行结果。
     */
    public String author() { return author; }
    /**
     * 处理description。
     * @return 方法执行结果。
     */
    public String description() { return description; }
    /**
     * 处理入口。
     * @return 方法执行结果。
     */
    public String entrypoint() { return entrypoint; }
}
