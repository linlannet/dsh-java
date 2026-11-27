package net.linlan.dsh.domain.connector;

/** 各连接器插件实现的服务端账号端口；凭据来源与 OAuth 客户端配置均留在服务端。 */
public interface IConnectorAccountPort {

    String connectorId();

    /** 发起连接或再次授权；连接中的账号也允许调用。 */
    ConnectorConnection connect();

    /** 使用一次性 OAuth 回调参数换取服务端令牌。 */
    default ConnectorConnection completeAuthorization(String code, String state) {
        throw new ConnectorAccountException("OAUTH_UNSUPPORTED", "该连接器不支持 OAuth 授权", false);
    }

    /** 刷新现有授予；默认明确表示不支持，不伪造成功状态。 */
    default ConnectorConnection refresh(ConnectorTokenGrant current) {
        throw new ConnectorAccountException("REFRESH_UNSUPPORTED", "该连接器不支持被动刷新，请重新授权", false);
    }

    /** 可选的远端吊销；本地密文缓存无论远端结果如何都会清理。 */
    default void disconnect(ConnectorTokenGrant current) {
    }
}
