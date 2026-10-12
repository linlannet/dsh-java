package net.linlan.dsh.infrastructure.adapter.llm;

import net.linlan.dsh.domain.llm.adapter.LlmAdapter;
import net.linlan.dsh.infrastructure.adapter.llm.anthropic.AnthropicAdapter;
import net.linlan.dsh.infrastructure.adapter.llm.openai.OpenAiCompatibleAdapter;
import net.linlan.dsh.infrastructure.adapter.llm.piai.PiAiAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LlmAdapterFactory 协议路由工厂")
class LlmAdapterFactoryTest {

    private LlmAdapterFactory factory;

    @BeforeEach
    void setUp() {
        factory = new LlmAdapterFactory(new ObjectMapper());
    }

    @Test
    @DisplayName("create 已知 provider 返回非 null 适配器，providerId 一致")
    void createKnownProviderReturnsAdapter() {
        LlmAdapter adapter = factory.create("deepseek");
        assertNotNull(adapter);
        assertEquals("deepseek", adapter.providerId());
    }

    @Test
    @DisplayName("create 未知 provider 仍返回适配器，providerId 为入参")
    void createUnknownProviderReturnsAdapter() {
        LlmAdapter adapter = factory.create("totally-unknown-provider");
        assertNotNull(adapter);
        assertEquals("totally-unknown-provider", adapter.providerId());
    }

    @Test
    @DisplayName("create 返回 ProtocolRoutingAdapter 实例")
    void createReturnsProtocolRoutingAdapter() {
        LlmAdapter adapter = factory.create("qwen");
        assertInstanceOf(ProtocolRoutingAdapter.class, adapter);
    }

    @Test
    @DisplayName("createForProtocol anthropic 创建 AnthropicAdapter")
    void createForProtocolAnthropic() {
        LlmAdapter adapter = factory.createForProtocol("anthropic", "anthropic");
        assertNotNull(adapter);
        assertInstanceOf(AnthropicAdapter.class, adapter);
        assertEquals("anthropic", adapter.providerId());
    }

    @Test
    @DisplayName("createForProtocol pi-ai 创建 PiAiAdapter")
    void createForProtocolPiAi() {
        LlmAdapter adapter = factory.createForProtocol("pi-ai-prov", "pi-ai");
        assertNotNull(adapter);
        assertInstanceOf(PiAiAdapter.class, adapter);
    }

    @Test
    @DisplayName("createForProtocol openai 创建 OpenAiCompatibleAdapter")
    void createForProtocolOpenai() {
        LlmAdapter adapter = factory.createForProtocol("deepseek", "openai");
        assertNotNull(adapter);
        assertInstanceOf(OpenAiCompatibleAdapter.class, adapter);
        assertEquals("deepseek", adapter.providerId());
    }

    @Test
    @DisplayName("createForProtocol ollama 创建 OpenAiCompatibleAdapter（兼容 OpenAI 协议）")
    void createForProtocolOllama() {
        LlmAdapter adapter = factory.createForProtocol("ollama", "ollama");
        assertNotNull(adapter);
        assertInstanceOf(OpenAiCompatibleAdapter.class, adapter);
    }

    @Test
    @DisplayName("createForProtocol 空协议回退为 OpenAI 兼容")
    void createForProtocolBlankFallsBackToOpenai() {
        LlmAdapter adapter = factory.createForProtocol("p", "");
        assertInstanceOf(OpenAiCompatibleAdapter.class, adapter);
    }

    @Test
    @DisplayName("createForProtocol 未知协议回退为 OpenAI 兼容")
    void createForProtocolUnknownFallsBackToOpenai() {
        LlmAdapter adapter = factory.createForProtocol("p", "unknown-protocol");
        assertInstanceOf(OpenAiCompatibleAdapter.class, adapter);
    }

    @Test
    @DisplayName("createForProtocol null 协议回退为 OpenAI 兼容")
    void createForProtocolNullFallsBackToOpenai() {
        LlmAdapter adapter = factory.createForProtocol("p", null);
        assertInstanceOf(OpenAiCompatibleAdapter.class, adapter);
    }

    @Test
    @DisplayName("create ollama 后 routing adapter 的 listModels 返回空列表")
    void createOllamaListModelsEmpty() {
        LlmAdapter adapter = factory.create("ollama");
        assertTrue(adapter.listModels("ollama").isEmpty());
    }

    @Test
    @DisplayName("不同 provider 创建独立实例")
    void differentProvidersCreateIndependentInstances() {
        LlmAdapter a = factory.create("deepseek");
        LlmAdapter b = factory.create("qwen");
        assertNotSame(a, b);
        assertEquals("deepseek", a.providerId());
        assertEquals("qwen", b.providerId());
    }
}
