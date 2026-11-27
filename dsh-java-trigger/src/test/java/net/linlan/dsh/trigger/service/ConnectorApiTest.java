package net.linlan.dsh.trigger.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import net.linlan.dsh.api.IConnectorApi;
import net.linlan.dsh.api.dto.ConnectorAccountStatusDTO;
import net.linlan.dsh.api.response.Response;
import net.linlan.dsh.cases.connector.ConnectorAccountCaseImpl;
import net.linlan.dsh.cases.connector.ConnectorAccountFactory;
import net.linlan.dsh.domain.connector.ConnectorAccountRegistry;
import net.linlan.dsh.domain.connector.ConnectorAccountService;
import net.linlan.dsh.domain.connector.ConnectorConnection;
import net.linlan.dsh.domain.connector.ConnectorTokenGrant;
import net.linlan.dsh.domain.connector.IConnectorAccountPort;
import net.linlan.dsh.domain.connector.IConnectorTokenCache;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectorApiTest {

    @Test
    void accountApiSerializesStatusWithoutTokens() throws Exception {
        TestCache cache = new TestCache();
        IConnectorAccountPort provider = new IConnectorAccountPort() {
            @Override
            public String connectorId() {
                return "notion";
            }

            @Override
            public ConnectorConnection connect() {
                return ConnectorConnection.authorized(
                        new ConnectorTokenGrant("api-secret-access", "api-secret-refresh", null));
            }
        };
        ConnectorAccountService service = new ConnectorAccountService(
                new ConnectorAccountRegistry(java.util.List.of(provider)), cache);
        ConnectorApi api = new ConnectorApi(new ConnectorAccountCaseImpl(new ConnectorAccountFactory(service)));

        Response<ConnectorAccountStatusDTO> response = api.connect("notion");
        assertEquals(Response.SUCCESS_CODE, response.code());
        assertTrue(response.data().connected());
        assertEquals("CONNECTED", response.data().state());

        ObjectMapper mapper = new ObjectMapper();
        SimpleModule instantModule = new SimpleModule();
        instantModule.addSerializer(Instant.class, ToStringSerializer.instance);
        mapper.registerModule(instantModule);
        String json = mapper.writeValueAsString(response);
        assertFalse(json.contains("api-secret-access"));
        assertFalse(json.contains("api-secret-refresh"));
        assertFalse(json.contains("accessToken"));
        assertFalse(json.contains("refreshToken"));

        assertEquals("api-secret-access", cache.find("notion").orElseThrow().accessToken());
        assertEquals("DISCONNECTED", api.disconnect("notion").data().state());
        assertTrue(cache.find("notion").isEmpty());
        assertEquals(7, api.listAssessments().data().size());
        var notion = api.listAssessments().data().stream()
                .filter(item -> item.connectorId().equals("notion")).findFirst().orElseThrow();
        assertEquals("Notion", notion.localizedNames().get("en-US"));
        assertFalse(notion.featurePreviews().get("zh-CN").isEmpty());
        assertFalse(notion.brandIcon().isBlank());
        assertEquals(Response.INVALID_ARGUMENT_CODE, api.getAccount("unknown").code());
    }

    @Test
    void publicDtoAndServiceContractHaveNoTokenProperties() {
        assertTrue(Arrays.stream(ConnectorAccountStatusDTO.class.getRecordComponents())
                .noneMatch(component -> component.getName().toLowerCase().contains("token")));
        assertTrue(Arrays.stream(IConnectorApi.class.getMethods())
                .noneMatch(method -> method.getName().toLowerCase().contains("token")));
    }

    private static final class TestCache implements IConnectorTokenCache {
        private final Map<String, ConnectorTokenGrant> grants = new ConcurrentHashMap<>();

        @Override
        public void save(String connectorId, ConnectorTokenGrant tokenGrant) {
            grants.put(connectorId, tokenGrant);
        }

        @Override
        public Optional<ConnectorTokenGrant> find(String connectorId) {
            return Optional.ofNullable(grants.get(connectorId));
        }

        @Override
        public void remove(String connectorId) {
            grants.remove(connectorId);
        }
    }
}
