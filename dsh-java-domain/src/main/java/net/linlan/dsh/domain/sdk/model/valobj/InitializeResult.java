package net.linlan.dsh.domain.sdk.model.valobj;

/**
 * Wire-stable server identity returned by initialization.
 *
 * @param serverName    wire-stable server identity
 * @param serverVersion server version
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
public record InitializeResult(String serverName, String serverVersion) {
    public static final String SERVER_NAME = "dsh-sdk-runtime";
}
