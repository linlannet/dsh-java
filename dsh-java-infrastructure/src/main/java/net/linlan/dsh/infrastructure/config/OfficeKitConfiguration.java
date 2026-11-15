package net.linlan.dsh.infrastructure.config;

import net.linlan.dsh.domain.document.office.model.valobj.OfficeKitConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OfficeKitConfiguration 将 dsh.office.* 配置转换为领域配置。
 */
@Configuration
public class OfficeKitConfiguration {

    /**
     * 装配 Office Kit 领域配置。
     *
     * @param properties 外部配置
     * @return 领域配置
     */
    @Bean
    public OfficeKitConfig officeKitConfig(OfficeKitProperties properties) {
        return new OfficeKitConfig(
                properties.getOutputDirectory(),
                properties.getOutputRetentionMs(),
                properties.getMaxOutputStoreBytes(),
                properties.getPythonEnvironment()
        );
    }
}
