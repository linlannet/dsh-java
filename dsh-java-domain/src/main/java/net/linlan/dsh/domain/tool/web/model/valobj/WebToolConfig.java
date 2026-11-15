package net.linlan.dsh.domain.tool.web.model.valobj;

import java.util.List;

/**
 * WebToolConfig 定义 Web 搜索/抓取的计费与凭据配置。
 *
 * <p>调用流程：配置绑定 → 凭据按有序来源解析 → 工具携带凭据请求。</p>
 * <p>示例：search=auto、credentials=[account, api-key] 时优先账户凭据。</p>
 *
 * @param searchBilling     搜索计费模式
 * @param fetchBilling      抓取计费模式
 * @param searchCredentials 搜索凭据来源（有序）
 * @param fetchCredentials  抓取凭据来源（有序）
 */
public record WebToolConfig(
        WebBillingMode searchBilling,
        WebBillingMode fetchBilling,
        List<WebCredentialSource> searchCredentials,
        List<WebCredentialSource> fetchCredentials,
        String searchEndpoint
) {

    public WebToolConfig {
        searchBilling = searchBilling == null ? WebBillingMode.AUTO : searchBilling;
        fetchBilling = fetchBilling == null ? WebBillingMode.AUTO : fetchBilling;
        searchCredentials = searchCredentials == null ? List.of() : List.copyOf(searchCredentials);
        fetchCredentials = fetchCredentials == null ? List.of() : List.copyOf(fetchCredentials);
        searchEndpoint = searchEndpoint == null ? "" : searchEndpoint;
    }

    /**
     * 返回默认配置：auto 计费、无凭据。
     *
     * @return 默认配置
     */
    public static WebToolConfig defaults() {
        return new WebToolConfig(WebBillingMode.AUTO, WebBillingMode.AUTO,
                List.of(), List.of(), "");
    }
}
