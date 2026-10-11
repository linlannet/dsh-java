package net.linlan.dsh.domain.spi;

/**
 * 插件 Hook 的函数式回调。
 * <p>插件实现后通过 {@link PluginContext#registerHook} 注册。
 * PRE_TOOL_USE 返回 BLOCK 或 DENY 会阻止工具执行；POST_TOOL_USE 的返回值只用于观察。
 * <p>数据示例：PRE_TOOL_USE 中 toolName="shell_execute"，返回 block("危险命令") 后工具不会执行。
 */
@FunctionalInterface
public interface PluginHook {

    /**
     * 执行 Hook。
     *
     * @param toolName    当前调用的工具名；非工具类 Hook 点可为 null
     * @param payloadJson JSON 格式的事件负载
     * @return Hook 结果；返回 null 等价于“不表达意见”
     */
    PluginHookResult execute(String toolName, String payloadJson);
}
