package net.linlan.dsh.domain.model.entity;

/**
 * 消息或注入内容的来源。
 * <p>
 * 对应 TS 侧 {@code MessageSource} 的密封层级。插件可以通过扩展 sealed permits
 * 列表添加自己的 {@code kind}。
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
public sealed interface MessageSource permits
        MessageSource.UserSource,
        MessageSource.PluginSource,
        MessageSource.ModelMessageSource,
        MessageSource.ToolMessageSource {

    /** 由人类用户产生的消息。 */
    record UserSource() implements MessageSource {}

    /** 由插件产生的消息。 */
    record PluginSource(String plugin) implements MessageSource {
        public PluginSource {
            java.util.Objects.requireNonNull(plugin, "plugin");
        }
    }

    /** 由模型产生的消息。 */
    record ModelMessageSource(String provider, String model) implements MessageSource {
        public ModelMessageSource {
            java.util.Objects.requireNonNull(provider, "provider");
            java.util.Objects.requireNonNull(model, "model");
        }
    }

    /** 携带工具结果的消息。 */
    record ToolMessageSource(String callId) implements MessageSource {
        public ToolMessageSource {
            java.util.Objects.requireNonNull(callId, "callId");
        }
    }
}
