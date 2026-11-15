package net.linlan.dsh.domain.tool.web.model.valobj;

import java.util.Locale;

/**
 * WebBillingMode 定义 Web 搜索/抓取的计费方式。
 *
 * <p>调用流程：配置解析 → 凭据解析 → 请求携带凭据。</p>
 * <p>示例：{@code auto} 按有序来源选择，{@code api-key} 仅使用 API Key。</p>
 */
public enum WebBillingMode {
    AUTO,
    ACCOUNT,
    API_KEY;

    /**
     * 解析计费模式，空值回落到 auto。
     *
     * @param raw 原始配置值
     * @return 计费模式
     */
    public static WebBillingMode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return AUTO;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "auto" -> AUTO;
            case "account" -> ACCOUNT;
            case "api-key", "api_key", "apikey" -> API_KEY;
            default -> throw new IllegalArgumentException("unsupported web billing mode: " + raw);
        };
    }

    /**
     * 返回配置值。
     *
     * @return 小写配置值
     */
    public String configValue() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
