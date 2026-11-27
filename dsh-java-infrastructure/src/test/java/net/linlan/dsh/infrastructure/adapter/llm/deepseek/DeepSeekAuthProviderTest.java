package net.linlan.dsh.infrastructure.adapter.llm.deepseek;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.linlan.dsh.infrastructure.adapter.llm.LlmAdapterFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpRequest;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DeepSeek 请求认证")
class DeepSeekAuthProviderTest {

    @Test
    @DisplayName("提供方解析请求凭据并签署 Authorization")
    void resolvesAndSignsRequest() {
        DeepSeekAuthProvider provider = DeepSeekAuthProvider.bearer("configured-key");
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1/v1/models")).GET();

        provider.sign(builder, null);
        HttpRequest request = builder.build();

        assertEquals("Bearer configured-key", request.headers().firstValue("Authorization").orElseThrow());
    }

    @Test
    @DisplayName("缺少认证提供方时启动失败并给出配置指引")
    void missingProviderFailsAtStartup() {
        DeepSeekAdapterOptions options = options();

        DeepSeekAuthError adapterError = assertThrows(DeepSeekAuthError.class,
                () -> new DeepSeekAdapter(options, new ObjectMapper(), null));
        DeepSeekAuthError factoryError = assertThrows(DeepSeekAuthError.class,
                () -> new LlmAdapterFactory(new ObjectMapper(), null));

        assertEquals(DeepSeekAuthError.AUTH_MISSING, adapterError.errorCode());
        assertTrue(adapterError.getMessage().contains("harness.llm.deepseek.api-key"));
        assertTrue(factoryError.getMessage().contains("DEEPSEEK_API_KEY"));
    }

    @Test
    @DisplayName("凭据缺失由认证提供方报告稳定错误码")
    void missingCredentialIsClassified() {
        DeepSeekAuthProvider provider = DeepSeekAuthProvider.bearer("");
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1/v1/models")).GET();

        DeepSeekAuthError error = assertThrows(DeepSeekAuthError.class, () -> provider.sign(builder, null));

        assertEquals(DeepSeekAuthError.AUTH_MISSING, error.errorCode());
        assertTrue(error.getMessage().contains("active model setting"));
    }

    static DeepSeekAdapterOptions options() {
        return new DeepSeekAdapterOptions("http://127.0.0.1/v1", "", 4096, 128_000,
                30_000, List.of(), null);
    }
}
