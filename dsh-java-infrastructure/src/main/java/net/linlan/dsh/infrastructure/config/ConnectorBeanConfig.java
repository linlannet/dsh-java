package net.linlan.dsh.infrastructure.config;

import net.linlan.dsh.domain.connector.ConnectorAccountRegistry;
import net.linlan.dsh.domain.connector.ConnectorAccountService;
import net.linlan.dsh.domain.connector.IConnectorAccountPort;
import net.linlan.dsh.domain.connector.IConnectorTokenCache;
import net.linlan.dsh.infrastructure.adapter.connector.EncryptedConnectorTokenCache;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 连接器账号套件装配；第三方提供方仍由所属插件按端口注册。 */
@Configuration
public class ConnectorBeanConfig {

    @Bean
    public ConnectorAccountRegistry connectorAccountRegistry(ObjectProvider<IConnectorAccountPort> accountPorts) {
        return new ConnectorAccountRegistry(accountPorts.orderedStream().toList());
    }

    @Bean
    public IConnectorTokenCache connectorTokenCache(
            @Value("${harness.connectors.token-encryption-key:${DSH_CONNECTOR_TOKEN_KEY:}}") String encryptionKey) {
        return new EncryptedConnectorTokenCache(encryptionKey);
    }

    @Bean
    public ConnectorAccountService connectorAccountService(ConnectorAccountRegistry registry,
                                                           IConnectorTokenCache tokenCache) {
        return new ConnectorAccountService(registry, tokenCache);
    }
}
