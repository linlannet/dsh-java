package net.linlan.dsh.domain.tool.web;

import net.linlan.dsh.domain.tool.web.adapter.port.IWebPort;
import net.linlan.dsh.domain.tool.web.model.error.WebBillingError;
import net.linlan.dsh.domain.tool.web.model.valobj.WebBillingMode;
import net.linlan.dsh.domain.tool.web.model.valobj.WebCredentialSource;
import net.linlan.dsh.domain.tool.web.model.valobj.WebToolConfig;
import net.linlan.dsh.domain.model.entity.ToolExecutionResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("Web 计费与凭据")
class WebBillingTest {

    private final WebCredentialResolver resolver = new WebCredentialResolver();
    private final List<WebCredentialSource> ordered = List.of(
            new WebCredentialSource(WebCredentialSource.ACCOUNT, "acct-1"),
            new WebCredentialSource(WebCredentialSource.API_KEY, "sk-1"));

    @Test
    @DisplayName("auto/account/api-key 三模式解析")
    void parsesModes() {
        assertEquals(WebBillingMode.AUTO, WebBillingMode.parse(null));
        assertEquals(WebBillingMode.AUTO, WebBillingMode.parse("auto"));
        assertEquals(WebBillingMode.ACCOUNT, WebBillingMode.parse("ACCOUNT"));
        assertEquals(WebBillingMode.API_KEY, WebBillingMode.parse("api-key"));
        assertEquals("api-key", WebBillingMode.API_KEY.configValue());
        assertThrows(IllegalArgumentException.class, () -> WebBillingMode.parse("free"));
    }

    @Test
    @DisplayName("auto 按有序来源选择第一条凭据")
    void autoUsesOrderedSources() {
        assertEquals("acct-1", resolver.resolve(WebBillingMode.AUTO, ordered).value());
        assertEquals(WebCredentialSource.ACCOUNT,
                resolver.resolve(WebBillingMode.AUTO, ordered).type());
    }

    @Test
    @DisplayName("api-key 模式跳过账户凭据")
    void apiKeyModeSkipsAccount() {
        var credential = resolver.resolve(WebBillingMode.API_KEY, ordered);
        assertEquals("sk-1", credential.value());
        assertEquals(WebCredentialSource.API_KEY, credential.type());
    }

    @Test
    @DisplayName("account 模式缺少账户凭据返回 WEB_ACCOUNT_UNAVAILABLE")
    void accountModeRequiresAccountCredential() {
        var error = assertThrows(WebBillingError.class,
                () -> resolver.resolve(WebBillingMode.ACCOUNT,
                        List.of(new WebCredentialSource(WebCredentialSource.API_KEY, "sk-1"))));
        assertEquals(WebBillingError.ACCOUNT_UNAVAILABLE, error.errorCode());
        assertEquals("WebBillingError", error.errorName());
    }

    @Test
    @DisplayName("无可用凭据返回 WEB_CREDENTIAL_MISSING")
    void missingCredential() {
        var error = assertThrows(WebBillingError.class,
                () -> resolver.resolve(WebBillingMode.AUTO,
                        List.of(new WebCredentialSource(WebCredentialSource.ACCOUNT, "  "))));
        assertEquals(WebBillingError.CREDENTIAL_MISSING, error.errorCode());
    }

    @Test
    @DisplayName("凭据错误保留 cause")
    void billingErrorKeepsCause() {
        var cause = new IOException("vault unavailable");
        var error = new WebBillingError(WebBillingError.CREDENTIAL_MISSING, "missing", cause);
        assertSame(cause, error.getCause());
    }

    @Test
    @DisplayName("web_search 使用解析后的账户凭据")
    void searchToolPassesCredential() {
        var web = new CapturingWeb();
        var config = new WebToolConfig(WebBillingMode.AUTO, WebBillingMode.AUTO,
                ordered, List.of(), "");
        var result = new WebSearchTool(web, config)
                .execute(Map.of("query", "java"), null).join();

        assertInstanceOf(ToolExecutionResult.Success.class, result);
        assertEquals("acct-1", web.lastSearchCredential);
    }

    @Test
    @DisplayName("web_fetch 使用解析后的 API Key")
    void fetchToolPassesCredential() {
        var web = new CapturingWeb();
        var config = new WebToolConfig(WebBillingMode.AUTO, WebBillingMode.API_KEY,
                List.of(), ordered, "");
        var result = new WebFetchTool(web, config)
                .execute(Map.of("url", "https://example.com"), null).join();

        assertInstanceOf(ToolExecutionResult.Success.class, result);
        assertEquals("sk-1", web.lastFetchCredential);
    }

    @Test
    @DisplayName("默认配置不解析凭据，沿用端口默认值")
    void defaultConfigLeavesCredentialNull() {
        var web = new CapturingWeb();
        new WebSearchTool(web).execute(Map.of("query", "java"), null).join();
        new WebFetchTool(web).execute(Map.of("url", "https://example.com"), null).join();

        assertNull(web.lastSearchCredential);
        assertNull(web.lastFetchCredential);
    }

    @Test
    @DisplayName("account 模式缺凭据时工具返回 WEB_ACCOUNT_UNAVAILABLE")
    void toolSurfacesBillingError() {
        var config = new WebToolConfig(WebBillingMode.ACCOUNT, WebBillingMode.ACCOUNT,
                List.of(), List.of(), "");
        var result = new WebSearchTool(new CapturingWeb(), config)
                .execute(Map.of("query", "java"), null).join();

        var failure = assertInstanceOf(ToolExecutionResult.Failure.class, result);
        assertEquals(WebBillingError.ACCOUNT_UNAVAILABLE, failure.error().info().code());
    }

    private static final class CapturingWeb implements IWebPort {
        String lastSearchCredential;
        String lastFetchCredential;

        @Override
        public SearchResult search(String query, int maxResults) {
            return search(query, maxResults, null);
        }

        @Override
        public SearchResult search(String query, int maxResults, String credential) {
            lastSearchCredential = credential;
            return new SearchResult("ok", List.of(), false);
        }

        @Override
        public FetchResult fetch(String url, int maxChars) {
            return fetch(url, maxChars, null);
        }

        @Override
        public FetchResult fetch(String url, int maxChars, String credential) {
            lastFetchCredential = credential;
            return new FetchResult(url, "body", "title", "text/plain");
        }
    }
}
