package net.linlan.dsh.domain.spi;

/**
 * 插件向宿主注册连接器账号提供方时的最小契约（位于 types，避免 types→domain 循环依赖）。
 * <p>完整行为端口 {@code net.linlan.dsh.domain.connector.IConnectorAccountPort} 继承本接口，
 * 插件只需实现该领域端口并在 {@link PluginContext#registerConnectorAccount} 中登记。
 */
public interface ConnectorAccountProvider {

    /** 与评估目录对齐的连接器标识，仅允许小写字母、数字、点、下划线与短横线。 */
    String connectorId();
}
