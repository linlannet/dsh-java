package net.linlan.dsh.infrastructure.config;

import net.linlan.dsh.domain.tool.web.model.valobj.WebBillingMode;
import net.linlan.dsh.domain.tool.web.model.valobj.WebCredentialSource;
import net.linlan.dsh.domain.tool.web.model.valobj.WebToolConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * WebToolConfiguration 将 harness.web.* 配置转换为领域配置。
 */
@Configuration
public class WebToolConfiguration {

    /**
     * 装配 Web 工具计费与凭据配置。
     *
     * @param properties 外部配置
     * @return Web 工具配置
     */
    @Bean
    public WebToolConfig webToolConfig(WebToolProperties properties) {
        return new WebToolConfig(
                WebBillingMode.parse(properties.getSearchBilling()),
                WebBillingMode.parse(properties.getFetchBilling()),
                credentials(properties.getSearchCredentials(), properties.getSearchApiKey()),
                credentials(properties.getFetchCredentials(), properties.getFetchApiKey()),
                properties.getSearchApiBase()
        );
    }

    private static List<WebCredentialSource> credentials(List<WebToolProperties.Credential> configured,
                                                         String fallbackKey) {
        List<WebCredentialSource> sources = new ArrayList<>();
        if (configured != null) {
            for (WebToolProperties.Credential credential : configured) {
                if (credential != null && credential.getValue() != null) {
                    sources.add(new WebCredentialSource(credential.getType(), credential.getValue()));
                }
            }
        }
        if (sources.isEmpty() && fallbackKey != null && !fallbackKey.isBlank()) {
            sources.add(new WebCredentialSource(WebCredentialSource.API_KEY, fallbackKey));
        }
        return sources;
    }
}
