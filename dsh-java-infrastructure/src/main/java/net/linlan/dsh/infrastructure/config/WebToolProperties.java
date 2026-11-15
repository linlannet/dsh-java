package net.linlan.dsh.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * WebToolProperties 绑定 harness.web.* 计费与凭据配置。
 *
 * <p>调用流程：配置绑定 → 转换有序凭据来源 → 工具解析。</p>
 * <p>示例：{@code harness.web.search-billing=api-key}。</p>
 */
@Component
@ConfigurationProperties(prefix = "harness.web")
public class WebToolProperties {

    private String searchBilling = "auto";
    private String fetchBilling = "auto";
    private String searchApiBase = "";
    private String searchApiKey = "";
    private String fetchApiKey = "";
    private List<Credential> searchCredentials = new ArrayList<>();
    private List<Credential> fetchCredentials = new ArrayList<>();

    public String getSearchBilling() {
        return searchBilling;
    }

    public void setSearchBilling(String searchBilling) {
        this.searchBilling = searchBilling;
    }

    public String getFetchBilling() {
        return fetchBilling;
    }

    public void setFetchBilling(String fetchBilling) {
        this.fetchBilling = fetchBilling;
    }

    public String getSearchApiBase() {
        return searchApiBase;
    }

    public void setSearchApiBase(String searchApiBase) {
        this.searchApiBase = searchApiBase;
    }

    public String getSearchApiKey() {
        return searchApiKey;
    }

    public void setSearchApiKey(String searchApiKey) {
        this.searchApiKey = searchApiKey;
    }

    public String getFetchApiKey() {
        return fetchApiKey;
    }

    public void setFetchApiKey(String fetchApiKey) {
        this.fetchApiKey = fetchApiKey;
    }

    public List<Credential> getSearchCredentials() {
        return searchCredentials;
    }

    public void setSearchCredentials(List<Credential> searchCredentials) {
        this.searchCredentials = searchCredentials;
    }

    public List<Credential> getFetchCredentials() {
        return fetchCredentials;
    }

    public void setFetchCredentials(List<Credential> fetchCredentials) {
        this.fetchCredentials = fetchCredentials;
    }

    /**
     * 凭据来源配置项。
     */
    public static class Credential {
        private String type;
        private String value;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }
}
