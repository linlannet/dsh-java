package net.linlan.dsh.infrastructure.adapter.llm.piai;

import net.linlan.dsh.infrastructure.adapter.llm.deepseek.DeepSeekAdapterOptions;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PiAiAdapter Pi-AI 适配器")
class PiAiAdapterTest {

    @Test
    @DisplayName("providerId 返回 pi-ai")
    void providerIdIsPiAi() {
        var options = new DeepSeekAdapterOptions(
                "https://api.pi-ai.com/v1", "test-key",
                8_192, 128_000, 300_000, List.of(), null);
        var adapter = new PiAiAdapter(options, new ObjectMapper());
        assertEquals("pi-ai", adapter.providerId());
    }

    @Test
    @DisplayName("PiAiAdapter 是 LlmAdapter 实例")
    void isLlmAdapter() {
        var options = new DeepSeekAdapterOptions(
                "https://api.pi-ai.com/v1", "test-key",
                8_192, 128_000, 300_000, List.of(), null);
        var adapter = new PiAiAdapter(options, new ObjectMapper());
        assertInstanceOf(net.linlan.dsh.domain.llm.adapter.LlmAdapter.class, adapter);
    }
}
