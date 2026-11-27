package net.linlan.dsh.domain.connector;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectorAccountServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-27T10:00:00Z");

    @Test
    void connectsRefreshesAndDisconnectsThroughOneService() {
        MutableClock clock = new MutableClock(NOW);
        TestTokenCache cache = new TestTokenCache();
        FakeProvider provider = new FakeProvider("notion");
        provider.connectResults.add(ConnectorConnection.authorized(
                new ConnectorTokenGrant("access-1", "refresh-1", NOW.plus(Duration.ofHours(1)))));
        provider.refreshHandler = current -> {
            assertEquals("refresh-1", current.refreshToken());
            return ConnectorConnection.authorized(
                    new ConnectorTokenGrant("access-2", "refresh-2", NOW.plus(Duration.ofHours(2))));
        };
        ConnectorAccountService service = service(provider, cache, clock);

        ConnectorAccountStatus connected = service.connect("notion");
        assertEquals(ConnectorAccountState.CONNECTED, connected.state());
        assertEquals("access-1", cache.find("notion").orElseThrow().accessToken());

        ConnectorAccountStatus refreshed = service.refresh("notion");
        assertEquals(ConnectorAccountState.CONNECTED, refreshed.state());
        assertEquals("access-2", cache.find("notion").orElseThrow().accessToken());
        assertEquals(1, provider.refreshCalls);

        ConnectorAccountStatus disconnected = service.disconnect("notion");
        assertEquals(ConnectorAccountState.DISCONNECTED, disconnected.state());
        assertTrue(cache.find("notion").isEmpty());
        assertEquals(1, provider.disconnectCalls);
        assertTrue(service.listStatus().get(0).diagnostic() == null);
    }

    @Test
    void oauthValidatesStateAndAllowsReauthorizationWhileConnected() {
        MutableClock clock = new MutableClock(NOW);
        TestTokenCache cache = new TestTokenCache();
        FakeProvider provider = new FakeProvider("notion");
        provider.connectResults.add(ConnectorConnection.authorizationRequired("https://auth.example/1", "state-1"));
        provider.connectResults.add(ConnectorConnection.authorizationRequired("https://auth.example/2", "state-2"));
        provider.connectResults.add(ConnectorConnection.authorizationRequired("https://auth.example/3", "state-3"));
        provider.authorizationHandler = (code, state) -> ConnectorConnection.authorized(
                new ConnectorTokenGrant("access-" + code, "refresh-" + code, NOW.plus(Duration.ofHours(1))));
        ConnectorAccountService service = service(provider, cache, clock);

        assertEquals(ConnectorAccountState.AUTHORIZATION_REQUIRED, service.connect("notion").state());
        ConnectorAccountStatus rejected = service.completeAuthorization("notion", "bad", "wrong-state");
        assertEquals(ConnectorAccountState.ERROR, rejected.state());
        assertEquals("AUTHORIZATION_STATE_MISMATCH", rejected.diagnostic().code());
        assertEquals(0, provider.authorizationCalls);

        service.connect("notion");
        ConnectorAccountStatus connected = service.completeAuthorization("notion", "first", "state-2");
        assertEquals(ConnectorAccountState.CONNECTED, connected.state());
        assertEquals("access-first", cache.find("notion").orElseThrow().accessToken());

        ConnectorAccountStatus reauthorizing = service.connect("notion");
        assertEquals(ConnectorAccountState.AUTHORIZATION_REQUIRED, reauthorizing.state());
        assertEquals("https://auth.example/3", reauthorizing.authorizationUrl());
        assertEquals("access-first", cache.find("notion").orElseThrow().accessToken());
        assertEquals(ConnectorAccountState.CONNECTED,
                service.completeAuthorization("notion", "second", "state-3").state());
        assertEquals("access-second", cache.find("notion").orElseThrow().accessToken());
    }

    @Test
    void statusPassivelyRefreshesOnlyExpiredGrant() {
        MutableClock clock = new MutableClock(NOW);
        TestTokenCache cache = new TestTokenCache();
        FakeProvider provider = new FakeProvider("linear");
        provider.connectResults.add(ConnectorConnection.authorized(
                new ConnectorTokenGrant("old-access", "old-refresh", NOW.plus(Duration.ofMinutes(1)))));
        provider.refreshHandler = current -> ConnectorConnection.authorized(
                new ConnectorTokenGrant("new-access", "new-refresh", NOW.plus(Duration.ofHours(1))));
        ConnectorAccountService service = service(provider, cache, clock);

        service.connect("linear");
        assertEquals(0, provider.refreshCalls);
        clock.advance(Duration.ofMinutes(2));
        ConnectorAccountStatus refreshed = service.status("linear");
        assertEquals(ConnectorAccountState.CONNECTED, refreshed.state());
        assertEquals(1, provider.refreshCalls);
        assertEquals("new-access", cache.find("linear").orElseThrow().accessToken());
        service.status("linear");
        assertEquals(1, provider.refreshCalls);
    }

    @Test
    void failedPassiveRefreshReturnsExpiredStateAndSafeDiagnostic() {
        MutableClock clock = new MutableClock(NOW);
        TestTokenCache cache = new TestTokenCache();
        FakeProvider provider = new FakeProvider("linear");
        provider.connectResults.add(ConnectorConnection.authorized(
                new ConnectorTokenGrant("private-access", "private-refresh", NOW)));
        provider.refreshHandler = current -> {
            throw new ConnectorAccountException("OAUTH_REFRESH_REJECTED", "授权已失效，请重新授权", false);
        };
        ConnectorAccountService service = service(provider, cache, clock);
        service.connect("linear");

        ConnectorAccountStatus expired = service.diagnose("linear");
        assertEquals(ConnectorAccountState.EXPIRED, expired.state());
        assertEquals("OAUTH_REFRESH_REJECTED", expired.diagnostic().code());
        assertFalse(expired.diagnostic().retryable());
        assertFalse(expired.diagnostic().message().contains("private-access"));
    }

    @Test
    void disconnectClearsLocalGrantEvenWhenRemoteRevokeFails() {
        TestTokenCache cache = new TestTokenCache();
        FakeProvider provider = new FakeProvider("wecom");
        provider.connectResults.add(ConnectorConnection.authorized(new ConnectorTokenGrant("secret", null, null)));
        provider.disconnectFailure = new RuntimeException("remote response must not leak: secret");
        ConnectorAccountService service = service(provider, cache, new MutableClock(NOW));
        service.connect("wecom");

        ConnectorAccountStatus status = service.disconnect("wecom");
        assertEquals(ConnectorAccountState.DISCONNECTED, status.state());
        assertEquals("DISCONNECT_FAILED", status.diagnostic().code());
        assertFalse(status.diagnostic().message().contains("secret"));
        assertTrue(cache.find("wecom").isEmpty());
    }

    @Test
    void refreshWithoutConnectionAndUnknownProviderAreExplicit() {
        TestTokenCache cache = new TestTokenCache();
        FakeProvider provider = new FakeProvider("dingtalk");
        ConnectorAccountService service = service(provider, cache, new MutableClock(NOW));

        ConnectorAccountStatus status = service.refresh("dingtalk");
        assertEquals("NOT_CONNECTED", status.diagnostic().code());
        assertThrows(IllegalArgumentException.class, () -> service.status("missing"));
    }

    @Test
    void assessmentCatalogHasDecisionAndAlternativeForEveryRequiredConnector() {
        Map<String, ConnectorAssessment> assessments = ConnectorAssessmentCatalog.all().stream()
                .collect(Collectors.toMap(ConnectorAssessment::connectorId, Function.identity()));
        assertEquals(Set.of("wecom", "dingtalk", "notion", "linear", "tencent-docs", "tencent-meeting", "feishu"),
                assessments.keySet());
        assessments.values().forEach(item -> {
            assertTrue(Set.of("接入", "不接入", "替代实现").contains(item.decision()));
            assertFalse(item.alternative().isBlank());
            assertFalse(item.javaStrategy().isBlank());
            assertFalse(item.brandIcon().isBlank());
            assertEquals(Set.of("zh-CN", "en-US"), item.localizedNames().keySet());
            assertEquals(Set.of("zh-CN", "en-US"), item.featurePreviews().keySet());
            assertFalse(item.localizedNames().get("zh-CN").isBlank());
            assertFalse(item.localizedNames().get("en-US").isBlank());
            assertFalse(item.featurePreviews().get("zh-CN").isEmpty());
            assertFalse(item.featurePreviews().get("en-US").isEmpty());
        });
        assertEquals("不接入", assessments.get("tencent-docs").decision());
        assertEquals("不接入", assessments.get("tencent-meeting").decision());
        assertNull(ConnectorAssessmentCatalog.find("unknown").orElse(null));
    }

    private static ConnectorAccountService service(FakeProvider provider, TestTokenCache cache, MutableClock clock) {
        return new ConnectorAccountService(new ConnectorAccountRegistry(java.util.List.of(provider)), cache, clock);
    }

    private static final class TestTokenCache implements IConnectorTokenCache {
        private final Map<String, ConnectorTokenGrant> values = new ConcurrentHashMap<>();

        @Override
        public void save(String connectorId, ConnectorTokenGrant tokenGrant) {
            values.put(connectorId, tokenGrant);
        }

        @Override
        public Optional<ConnectorTokenGrant> find(String connectorId) {
            return Optional.ofNullable(values.get(connectorId));
        }

        @Override
        public void remove(String connectorId) {
            values.remove(connectorId);
        }
    }

    private static final class FakeProvider implements IConnectorAccountPort {
        private final String connectorId;
        private final Queue<ConnectorConnection> connectResults = new ArrayDeque<>();
        private Function<ConnectorTokenGrant, ConnectorConnection> refreshHandler;
        private BiFunction<String, String, ConnectorConnection> authorizationHandler;
        private RuntimeException disconnectFailure;
        private int refreshCalls;
        private int authorizationCalls;
        private int disconnectCalls;

        private FakeProvider(String connectorId) {
            this.connectorId = connectorId;
        }

        @Override
        public String connectorId() {
            return connectorId;
        }

        @Override
        public ConnectorConnection connect() {
            return connectResults.remove();
        }

        @Override
        public ConnectorConnection completeAuthorization(String code, String state) {
            authorizationCalls++;
            return authorizationHandler.apply(code, state);
        }

        @Override
        public ConnectorConnection refresh(ConnectorTokenGrant current) {
            refreshCalls++;
            return refreshHandler.apply(current);
        }

        @Override
        public void disconnect(ConnectorTokenGrant current) {
            disconnectCalls++;
            if (disconnectFailure != null) {
                throw disconnectFailure;
            }
        }
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        private void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
