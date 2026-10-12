package net.linlan.dsh.domain.sdk;

import net.linlan.dsh.domain.sdk.model.valobj.InitializeParams;
import net.linlan.dsh.domain.sdk.model.valobj.InitializeResult;
import net.linlan.dsh.domain.sdk.model.valobj.McpToolInfo;
import net.linlan.dsh.domain.sdk.model.valobj.ResourceInfo;
import net.linlan.dsh.domain.sdk.model.valobj.SessionDetail;
import net.linlan.dsh.domain.sdk.model.valobj.SessionPromptParams;
import net.linlan.dsh.domain.sdk.model.valobj.SessionPromptResult;
import net.linlan.dsh.domain.sdk.model.valobj.SessionStatus;
import net.linlan.dsh.domain.sdk.model.valobj.SessionSummary;
import net.linlan.dsh.domain.sdk.model.valobj.ToolInfo;

import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ISdkServiceContractTest {

    @Test
    void serverServiceDeclaresAbstractLifecycleMethods() throws NoSuchMethodException {
        assertNotNull(ISdkServerService.class.getMethod("start"));
        assertNotNull(ISdkServerService.class.getMethod("stop"));
        assertNotNull(ISdkServerService.class.getMethod("emitNotification", String.class, Object.class));
    }

    @Test
    void serverServiceDeclaresListSessionsWithIntLimit() throws NoSuchMethodException {
        Method m = ISdkServerService.class.getMethod("listSessions", int.class);
        assertEquals(List.class, m.getReturnType());
    }

    @Test
    void serverServiceDeclaresLoadSessionReturningSessionDetail() throws NoSuchMethodException {
        Method m = ISdkServerService.class.getMethod("loadSession", String.class);
        assertEquals(SessionDetail.class, m.getReturnType());
    }

    @Test
    void serverServiceDeclaresCancelSessionReturningBoolean() throws NoSuchMethodException {
        Method m = ISdkServerService.class.getMethod("cancelSession", String.class);
        assertEquals(boolean.class, m.getReturnType());
    }

    @Test
    void serverServiceDeclaresStreamSessionWithConsumerSink() throws NoSuchMethodException {
        assertNotNull(ISdkServerService.class.getMethod("streamSession", String.class, Consumer.class));
    }

    @Test
    void serverServiceDeclaresToolAndResourceListingMethods() throws NoSuchMethodException {
        assertNotNull(ISdkServerService.class.getMethod("listTools", String.class));
        assertNotNull(ISdkServerService.class.getMethod("cancelTool", String.class, String.class));
        assertNotNull(ISdkServerService.class.getMethod("listMcpTools"));
        assertNotNull(ISdkServerService.class.getMethod("listResources", String.class));
    }

    @Test
    void clientServiceDeclaresAbstractRequestMethods() throws NoSuchMethodException {
        assertNotNull(ISdkClientService.class.getMethod("initialize", InitializeParams.class));
        assertNotNull(ISdkClientService.class.getMethod("sessionPrompt", SessionPromptParams.class));
        assertNotNull(ISdkClientService.class.getMethod("shutdown"));
        assertNotNull(ISdkClientService.class.getMethod("onSessionStatus", Consumer.class));
        assertNotNull(ISdkClientService.class.getMethod("onSessionEvent", Consumer.class));
    }

    @Test
    void serverServiceDefaultListSessionsReturnsEmptyList() {
        ISdkServerService server = bareServer();

        List<SessionSummary> sessions = server.listSessions(10);

        assertNotNull(sessions);
        assertTrue(sessions.isEmpty());
    }

    @Test
    void serverServiceDefaultLoadSessionReturnsNull() {
        ISdkServerService server = bareServer();

        assertNull(server.loadSession("any"));
    }

    @Test
    void serverServiceDefaultCancelSessionReturnsFalse() {
        ISdkServerService server = bareServer();

        assertFalse(server.cancelSession("any"));
    }

    @Test
    void serverServiceDefaultStreamSessionIsNoop() {
        ISdkServerService server = bareServer();
        boolean[] invoked = {false};

        server.streamSession("any", event -> invoked[0] = true);

        assertFalse(invoked[0]);
    }

    @Test
    void serverServiceDefaultListToolsReturnsEmptyList() {
        ISdkServerService server = bareServer();

        assertTrue(server.listTools("any").isEmpty());
    }

    @Test
    void serverServiceDefaultCancelToolReturnsFalse() {
        ISdkServerService server = bareServer();

        assertFalse(server.cancelTool("any", "call-1"));
    }

    @Test
    void serverServiceDefaultMcpAndResourceListingsReturnEmpty() {
        ISdkServerService server = bareServer();

        assertTrue(server.listMcpTools().isEmpty());
        assertTrue(server.listResources("scope").isEmpty());
    }

    @Test
    void clientServiceDefaultsMirrorServerDefaults() {
        ISdkClientService client = bareClient();

        assertTrue(client.listSessions(5).isEmpty());
        assertNull(client.loadSession("any"));
        assertFalse(client.cancelSession("any"));
        assertTrue(client.listTools("any").isEmpty());
        assertFalse(client.cancelTool("any", "call-1"));
        assertTrue(client.listMcpTools().isEmpty());
        assertTrue(client.listResources("scope").isEmpty());
    }

    @Test
    void serverServiceDefaultMethodCountMatchesExpectedContract() {
        long defaultCount = java.util.Arrays.stream(ISdkServerService.class.getDeclaredMethods())
                .filter(Method::isDefault)
                .count();

        assertEquals(8, defaultCount);
    }

    @Test
    void clientServiceDefaultMethodCountMatchesExpectedContract() {
        long defaultCount = java.util.Arrays.stream(ISdkClientService.class.getDeclaredMethods())
                .filter(Method::isDefault)
                .count();

        assertEquals(7, defaultCount);
    }

    @Test
    void serverAndClientShareSameDefaultMethodSignatures() throws NoSuchMethodException {
        for (String name : new String[]{"listSessions", "loadSession", "cancelSession",
                "listTools", "cancelTool", "listMcpTools", "listResources"}) {
            assertNotNull(ISdkServerService.class.getMethod(name, parameterTypesFor(name)));
            assertNotNull(ISdkClientService.class.getMethod(name, parameterTypesFor(name)));
        }
    }

    private Class<?>[] parameterTypesFor(String name) {
        return switch (name) {
            case "listSessions" -> new Class<?>[]{int.class};
            case "loadSession", "cancelSession", "listTools", "listResources" -> new Class<?>[]{String.class};
            case "cancelTool" -> new Class<?>[]{String.class, String.class};
            case "listMcpTools" -> new Class<?>[]{};
            default -> throw new IllegalArgumentException(name);
        };
    }

    private ISdkServerService bareServer() {
        return new ISdkServerService() {
            @Override
            public void start() { }
            @Override
            public void stop() { }
            @Override
            public void emitNotification(String method, Object params) { }
        };
    }

    private ISdkClientService bareClient() {
        return new ISdkClientService() {
            @Override
            public InitializeResult initialize(InitializeParams params) { return null; }
            @Override
            public SessionPromptResult sessionPrompt(SessionPromptParams params) { return null; }
            @Override
            public void shutdown() { }
            @Override
            public void onSessionStatus(Consumer<SessionStatus> handler) { }
            @Override
            public void onSessionEvent(Consumer<Object> handler) { }
        };
    }
}
