package net.linlan.dsh.domain.connector;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectorAccountRegistryTest {

    private static IConnectorAccountPort port(String id) {
        return new IConnectorAccountPort() {
            @Override public String connectorId() { return id; }
            @Override public ConnectorConnection connect() { throw new AssertionError(); }
        };
    }

    @Test
    void registersAndFindsProvider() {
        ConnectorAccountRegistry registry = new ConnectorAccountRegistry();
        IConnectorAccountPort feishu = port("feishu");

        registry.register(feishu);

        assertTrue(registry.find("feishu").isPresent());
        assertSame(feishu, registry.require("feishu"));
        assertEquals(List.of("feishu"), registry.connectorIds());
    }

    @Test
    void duplicateRegistrationRejected() {
        ConnectorAccountRegistry registry = new ConnectorAccountRegistry();
        registry.register(port("feishu"));

        assertThrows(IllegalArgumentException.class, () -> registry.register(port("feishu")));
    }

    @Test
    void unregisterRemovesProviderIdempotently() {
        ConnectorAccountRegistry registry = new ConnectorAccountRegistry();
        IConnectorAccountPort feishu = port("feishu");
        registry.register(feishu);

        var removed = registry.unregister("feishu");

        assertTrue(removed.isPresent());
        assertSame(feishu, removed.get());
        assertTrue(registry.find("feishu").isEmpty());
        assertTrue(registry.connectorIds().isEmpty());

        // 再次卸载不应抛错
        assertTrue(registry.unregister("feishu").isEmpty());
    }

    @Test
    void unregisterRejectsInvalidConnectorId() {
        ConnectorAccountRegistry registry = new ConnectorAccountRegistry();
        assertThrows(IllegalArgumentException.class, () -> registry.unregister("BAD_ID"));
    }

    @Test
    void connectorIdsSortedAndFiltered() {
        ConnectorAccountRegistry registry = new ConnectorAccountRegistry();
        registry.register(port("wecom"));
        registry.register(port("feishu"));

        assertEquals(List.of("feishu", "wecom"), registry.connectorIds());
        assertFalse(registry.find("notion").isPresent());
    }
}
