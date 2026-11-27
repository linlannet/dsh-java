package net.linlan.dsh.domain.connector;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** 插件账号端口与安全状态快照的进程内注册表。 */
public class ConnectorAccountRegistry {
    private final Map<String, IConnectorAccountPort> providers = new ConcurrentHashMap<>();
    private final Map<String, ConnectorAccountStatus> statuses = new ConcurrentHashMap<>();

    public ConnectorAccountRegistry() {
    }

    public ConnectorAccountRegistry(Iterable<? extends IConnectorAccountPort> accountPorts) {
        accountPorts.forEach(this::register);
    }

    public final void register(IConnectorAccountPort accountPort) {
        String connectorId = requireConnectorId(accountPort.connectorId());
        IConnectorAccountPort previous = providers.putIfAbsent(connectorId, accountPort);
        if (previous != null) {
            throw new IllegalArgumentException("connector account provider already registered: " + connectorId);
        }
    }

    public IConnectorAccountPort require(String connectorId) {
        return find(connectorId).orElseThrow(() ->
                new IllegalArgumentException("connector account provider not registered: " + connectorId));
    }

    public Optional<IConnectorAccountPort> find(String connectorId) {
        return Optional.ofNullable(providers.get(requireConnectorId(connectorId)));
    }

    public Optional<ConnectorAccountStatus> findStatus(String connectorId) {
        return Optional.ofNullable(statuses.get(requireConnectorId(connectorId)));
    }

    public List<String> connectorIds() {
        return providers.keySet().stream().sorted().toList();
    }

    void saveStatus(ConnectorAccountStatus status) {
        require(status.connectorId());
        statuses.put(status.connectorId(), status);
    }

    private static String requireConnectorId(String connectorId) {
        if (connectorId == null || !connectorId.matches("[a-z0-9][a-z0-9._-]*")) {
            throw new IllegalArgumentException("connectorId must contain only lowercase letters, digits, dot, underscore or dash");
        }
        return connectorId;
    }
}
