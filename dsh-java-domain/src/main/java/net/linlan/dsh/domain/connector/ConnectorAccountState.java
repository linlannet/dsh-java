package net.linlan.dsh.domain.connector;

/** 连接器账号对外可观察状态。 */
public enum ConnectorAccountState {
    DISCONNECTED,
    AUTHORIZATION_REQUIRED,
    CONNECTED,
    EXPIRED,
    ERROR
}
