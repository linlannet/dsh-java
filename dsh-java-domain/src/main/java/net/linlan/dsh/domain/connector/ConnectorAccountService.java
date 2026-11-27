package net.linlan.dsh.domain.connector;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** 统一连接、OAuth 回调、断开、刷新与分类诊断。 */
public class ConnectorAccountService {
    private static final Duration AUTHORIZATION_TTL = Duration.ofMinutes(10);

    private final ConnectorAccountRegistry registry;
    private final IConnectorTokenCache tokenCache;
    private final Clock clock;
    private final Map<String, PendingAuthorization> pendingAuthorizations = new ConcurrentHashMap<>();

    public ConnectorAccountService(ConnectorAccountRegistry registry, IConnectorTokenCache tokenCache) {
        this(registry, tokenCache, Clock.systemUTC());
    }

    public ConnectorAccountService(ConnectorAccountRegistry registry, IConnectorTokenCache tokenCache, Clock clock) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.tokenCache = Objects.requireNonNull(tokenCache, "tokenCache");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** 发起首次连接或再次授权；已有授予在新授权成功前仍只留在原密文缓存中。 */
    public ConnectorAccountStatus connect(String connectorId) {
        IConnectorAccountPort provider = registry.require(connectorId);
        pendingAuthorizations.remove(connectorId);
        try {
            return apply(connectorId, provider.connect());
        } catch (RuntimeException exception) {
            return failure(connectorId, "CONNECT_FAILED", exception, ConnectorAccountState.ERROR);
        }
    }

    /** 校验一次性 state 后完成 OAuth；回调只接收授权码，不接收前端提交的令牌。 */
    public ConnectorAccountStatus completeAuthorization(String connectorId, String code, String state) {
        IConnectorAccountPort provider = registry.require(connectorId);
        if (code == null || code.isBlank()) {
            return error(connectorId, "AUTHORIZATION_CODE_REQUIRED", "缺少 OAuth 授权码", false);
        }
        PendingAuthorization pending = pendingAuthorizations.get(connectorId);
        if (pending == null) {
            return error(connectorId, "AUTHORIZATION_NOT_STARTED", "请先发起连接或再次授权", false);
        }
        Instant now = clock.instant();
        if (!now.isBefore(pending.expiresAt())) {
            pendingAuthorizations.remove(connectorId);
            return error(connectorId, "AUTHORIZATION_EXPIRED", "OAuth 授权已过期，请重新发起", true);
        }
        if (!sameState(pending.state(), state)) {
            pendingAuthorizations.remove(connectorId);
            return error(connectorId, "AUTHORIZATION_STATE_MISMATCH", "OAuth state 校验失败，请重新授权", false);
        }
        pendingAuthorizations.remove(connectorId);
        try {
            return apply(connectorId, provider.completeAuthorization(code, state));
        } catch (RuntimeException exception) {
            return failure(connectorId, "AUTHORIZATION_FAILED", exception, ConnectorAccountState.ERROR);
        }
    }

    /** 读取状态时被动刷新已过期授予；无本地授予时不会伪报已连接。 */
    public ConnectorAccountStatus status(String connectorId) {
        registry.require(connectorId);
        ConnectorAccountStatus current = registry.findStatus(connectorId)
                .orElseGet(() -> disconnected(connectorId, null));
        Optional<ConnectorTokenGrant> grant;
        try {
            grant = tokenCache.find(connectorId);
        } catch (RuntimeException exception) {
            return failure(connectorId, "TOKEN_CACHE_UNAVAILABLE", exception, ConnectorAccountState.ERROR);
        }

        if ((current.state() == ConnectorAccountState.CONNECTED || current.state() == ConnectorAccountState.EXPIRED)
                && grant.isEmpty()) {
            return error(connectorId, "TOKEN_NOT_FOUND", "服务端令牌不可用，请重新连接", false);
        }
        if (current.state() == ConnectorAccountState.CONNECTED
                && grant.isPresent() && grant.get().isExpired(clock.instant())) {
            return refresh(connectorId);
        }
        if (current.state() == ConnectorAccountState.EXPIRED && grant.isPresent()) {
            return refresh(connectorId);
        }
        return current;
    }

    public ConnectorAccountStatus diagnose(String connectorId) {
        return status(connectorId);
    }

    public List<ConnectorAccountStatus> listStatus() {
        return registry.connectorIds().stream().map(this::status).toList();
    }

    /** 显式刷新；不提供刷新能力的端口返回可诊断状态而不是抛出原始第三方错误. */
    public ConnectorAccountStatus refresh(String connectorId) {
        IConnectorAccountPort provider = registry.require(connectorId);
        Optional<ConnectorTokenGrant> current;
        try {
            current = tokenCache.find(connectorId);
        } catch (RuntimeException exception) {
            return failure(connectorId, "TOKEN_CACHE_UNAVAILABLE", exception, ConnectorAccountState.ERROR);
        }
        if (current.isEmpty()) {
            return error(connectorId, "NOT_CONNECTED", "连接器尚未连接", false);
        }
        ConnectorTokenGrant grant = current.get();
        try {
            return apply(connectorId, provider.refresh(grant));
        } catch (RuntimeException exception) {
            ConnectorAccountState failureState = grant.isExpired(clock.instant())
                    ? ConnectorAccountState.EXPIRED : ConnectorAccountState.ERROR;
            return failure(connectorId, "REFRESH_FAILED", exception, failureState);
        }
    }

    /** 本地登出优先：即使远端吊销失败也清理密文授予和待授权状态。 */
    public ConnectorAccountStatus disconnect(String connectorId) {
        IConnectorAccountPort provider = registry.require(connectorId);
        RuntimeException problem = null;
        try {
            Optional<ConnectorTokenGrant> current = tokenCache.find(connectorId);
            if (current.isPresent()) {
                provider.disconnect(current.get());
            }
        } catch (RuntimeException exception) {
            problem = exception;
        }
        try {
            tokenCache.remove(connectorId);
        } catch (RuntimeException exception) {
            if (problem == null) {
                problem = exception;
            }
        }
        pendingAuthorizations.remove(connectorId);
        ConnectorDiagnostic diagnostic = problem == null ? null : diagnostic("DISCONNECT_FAILED", problem);
        return disconnected(connectorId, diagnostic);
    }

    public List<ConnectorAssessment> assessments() {
        return ConnectorAssessmentCatalog.all();
    }

    private ConnectorAccountStatus apply(String connectorId, ConnectorConnection connection) {
        if (connection == null) {
            throw new ConnectorAccountException("EMPTY_PROVIDER_RESULT", "连接器提供方未返回连接结果", true);
        }
        if (connection.authorizationRequired()) {
            pendingAuthorizations.put(connectorId, new PendingAuthorization(
                    connection.authorizationState(), clock.instant().plus(AUTHORIZATION_TTL)));
            return save(new ConnectorAccountStatus(connectorId, ConnectorAccountState.AUTHORIZATION_REQUIRED,
                    connection.authorizationUrl(), null, clock.instant(), null));
        }
        ConnectorTokenGrant grant = connection.tokenGrant();
        tokenCache.save(connectorId, grant);
        pendingAuthorizations.remove(connectorId);
        return save(new ConnectorAccountStatus(connectorId, ConnectorAccountState.CONNECTED,
                null, grant.expiresAt(), clock.instant(), null));
    }

    private ConnectorAccountStatus disconnected(String connectorId, ConnectorDiagnostic diagnostic) {
        return save(new ConnectorAccountStatus(connectorId, ConnectorAccountState.DISCONNECTED,
                null, null, clock.instant(), diagnostic));
    }

    private ConnectorAccountStatus error(String connectorId, String code, String message, boolean retryable) {
        return save(new ConnectorAccountStatus(connectorId, ConnectorAccountState.ERROR,
                null, null, clock.instant(), new ConnectorDiagnostic(code, message, retryable)));
    }

    private ConnectorAccountStatus failure(String connectorId, String fallbackCode, RuntimeException exception,
                                           ConnectorAccountState state) {
        return save(new ConnectorAccountStatus(connectorId, state, null, null, clock.instant(),
                diagnostic(fallbackCode, exception)));
    }

    private ConnectorAccountStatus save(ConnectorAccountStatus status) {
        registry.saveStatus(status);
        return status;
    }

    private static ConnectorDiagnostic diagnostic(String fallbackCode, RuntimeException exception) {
        if (exception instanceof ConnectorAccountException accountException) {
            return new ConnectorDiagnostic(accountException.errorCode(), accountException.getMessage(),
                    accountException.retryable());
        }
        return new ConnectorDiagnostic(fallbackCode,
                "连接器操作失败（" + exception.getClass().getSimpleName() + "）", true);
    }

    private static boolean sameState(String expected, String actual) {
        return actual != null && MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }

    private record PendingAuthorization(String state, Instant expiresAt) {
    }
}
