package net.linlan.dsh.domain.e2b.model.valobj;

/**
 * E2BSandboxEnv 表示E2BSandboxEnv。
 */
public record E2BSandboxEnv(String sandboxId, String apiKey, String template, long timeoutMs) {
    /**
     * 创建目标对象。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param template template参数。
     * @param timeoutMs 超时时间，毫秒。
     * @return 创建的领域对象。
     */
    public static E2BSandboxEnv of(String template, long timeoutMs) {
        return new E2BSandboxEnv(null, null, template, timeoutMs);
    }
}
