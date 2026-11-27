package net.linlan.dsh.domain.connector;

import java.util.Optional;

/** 服务端令牌缓存端口；实现必须保证持久内容不是明文。 */
public interface IConnectorTokenCache {

    void save(String connectorId, ConnectorTokenGrant tokenGrant);

    Optional<ConnectorTokenGrant> find(String connectorId);

    void remove(String connectorId);
}
