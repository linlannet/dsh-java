package net.linlan.dsh.domain.spi;

import java.util.Optional;

/**
 * 插件 Hook 产生的结果。四个字段都可选，Hook 可按需返回任意组合。
 * <p>BLOCK/DENY 会阻止当前动作；ALLOW/APPROVE 表示继续；缺省 decision 表示不表达意见。
 * <p>数据示例：block("禁止删除 /etc") 会阻止工具执行，并把原因放入 decision/reason。
 *
 * @param decision          BLOCK/DENY 表示停止，ALLOW/APPROVE 表示继续，null 表示无意见
 * @param reason            与决定一起返回的原因
 * @param additionalContext 附加到下一次模型请求的上下文
 * @param systemMessage     需要展示给用户的警告信息
 */
public record PluginHookResult(
        Optional<String> decision,
        Optional<String> reason,
        Optional<String> additionalContext,
        Optional<String> systemMessage
) {
    public PluginHookResult {
        decision = decision == null ? Optional.empty() : decision;
        reason = reason == null ? Optional.empty() : reason;
        additionalContext = additionalContext == null ? Optional.empty() : additionalContext;
        systemMessage = systemMessage == null ? Optional.empty() : systemMessage;
    }

    /**
     * 构造允许继续执行的结果。
     */
    public static PluginHookResult allow() {
        return new PluginHookResult(Optional.of("ALLOW"), Optional.empty(), Optional.empty(), Optional.empty());
    }

    /**
     * 构造阻止当前动作的结果。
     */
    public static PluginHookResult block(String reason) {
        return new PluginHookResult(Optional.of("BLOCK"), Optional.ofNullable(reason), Optional.empty(), Optional.empty());
    }

    /**
     * 构造拒绝当前动作的结果。
     */
    public static PluginHookResult deny(String reason) {
        return new PluginHookResult(Optional.of("DENY"), Optional.ofNullable(reason), Optional.empty(), Optional.empty());
    }

    /**
     * 构造只注入附加上下文、不改变执行决定的结果。
     */
    public static PluginHookResult context(String additionalContext) {
        return new PluginHookResult(Optional.empty(), Optional.empty(), Optional.ofNullable(additionalContext), Optional.empty());
    }
}
