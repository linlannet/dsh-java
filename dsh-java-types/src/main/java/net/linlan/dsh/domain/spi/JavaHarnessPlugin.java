package net.linlan.dsh.domain.spi;

import net.linlan.dsh.domain.model.entity.ToolDefinition;
import java.util.List;

/**
 * Java 原生 Harness 插件必须实现的 SPI 接口。
 * <p>
 * 插件以 JAR 包形式提供，内部包含 {@code META-INF/plugin.yaml} 清单和指向本接口
 * 实现类的 {@code META-INF/services/} ServiceLoader 文件。Harness 使用隔离的
 * {@link ClassLoader} 加载 JAR，通过 ServiceLoader 实例化插件，并把返回的
 * {@link ToolDefinition} 注册到工具注册表。
 * <p>
 * Plugin authors only depend on the {@code dsh-java-types}
 * module (this interface + {@link ToolDefinition}). No Spring types leak
 * into the plugin contract.
 *
 * <p><b>How to use</b></p>
 * <p>
 * <b>1. Maven 依赖（provided scope，JAR 不打入插件包）：</b>
 * <pre>{@code
 * <dependency>
     *     <groupId>net.linlan.dsh</groupId>
 *     <artifactId>dsh-java-types</artifactId>
 *     <version>1.0.0-SNAPSHOT</version>
 *     <scope>provided</scope>
 * </dependency>
 * }</pre>
 *
 * <p><b>2. 实现接口（或继承 {@link AbstractHarnessPlugin} 更省事）：</b>
 * <pre>{@code
 * public class MyPlugin implements JavaHarnessPlugin {
 *     @Override public String pluginId() { return "my-plugin"; }
 *     @Override public List<ToolDefinition> tools() { return List.of(new MyTool()); }
 *     @Override public void onStart() { /* 初始化 *\/ }
 *     @Override public void onStop()  { /* 释放 *\/ }
 * }
 * }</pre>
 *
 * <p><b>3. 打包两个清单文件到 JAR：</b>
 * <pre>
 * META-INF/plugin.yaml          → id / name / version / entrypoint
     * META-INF/services/net.linlan.dsh.domain.spi.JavaHarnessPlugin
 *                               → 实现类全限定名
 * </pre>
 *
 * <p><b>4. mvn package 打 JAR，放到 harness 的 plugins/ 目录</b>
 * <p><b>5. 通过 application.yml preset 或 HTTP API 安装加载</b>
 *
 * <p>推荐直接继承 {@link AbstractHarnessPlugin}，可省掉 pluginId 样板和日志装配。
 */
public interface JavaHarnessPlugin {

    /**
     * 插件 ID，必须与 {@code META-INF/plugin.yaml} 中的 {@code id} 字段一致。
     *
     * @return 稳定的插件 ID
     */
    String pluginId();

    /**
     * 返回本插件提供的所有工具定义。
     * <p>
     * 每个 {@link ToolDefinition} 会以 {@code plugin__<pluginId>__<toolName>}
     * 的完整名称暴露给 Agent 循环。
     *
     * @return 工具定义列表，永不为 {@code null}；仅使用 configure(PluginContext)
     *         注册非工具能力时可保留默认空集合
     */
    default List<ToolDefinition> tools() {
        return List.of();
    }

    /**
     * Contribute capabilities to the host. The default implementation keeps
     * compatibility with the original tools() contract. Plugins that need
     * deterministic registration order or non-tool resources may override it.
     */
    default void configure(PluginContext context) {
        for (ToolDefinition tool : tools()) {
            context.registerTool(tool);
        }
    }

    /**
     * 生命周期钩子：实例化后、首次工具调用前调用一次。
     * 可用于初始化资源、建立连接等。
     */
    default void onStart() {}

    /**
     * 生命周期钩子：插件停止或卸载时调用。
     * 可用于释放资源、关闭连接等。
     */
    default void onStop() {}
}
