package net.linlan.dsh.domain.agent.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("T2.035 AgentDefaultModelProvider")
class AgentDefaultModelProviderTest {

    @Test
    @DisplayName("getDefaultModel 未注册 preset 返回默认模型")
    void getDefaultModelReturnsDefaultWhenUnregistered() {
        AgentDefaultModelProvider provider = new AgentDefaultModelProvider("gpt-4o");

        String model = provider.getDefaultModel("unknown-preset");

        assertEquals("gpt-4o", model);
    }

    @Test
    @DisplayName("getDefaultModel 已注册 preset 返回对应模型")
    void getDefaultModelReturnsRegisteredModel() {
        AgentDefaultModelProvider provider = new AgentDefaultModelProvider("gpt-4o");
        provider.register("coder", "claude-3.5-sonnet");

        String model = provider.getDefaultModel("coder");

        assertEquals("claude-3.5-sonnet", model);
    }

    @Test
    @DisplayName("getDefaultModel null preset 返回默认模型")
    void getDefaultModelNullPresetReturnsDefault() {
        AgentDefaultModelProvider provider = new AgentDefaultModelProvider("gpt-4o");

        String model = provider.getDefaultModel(null);

        assertEquals("gpt-4o", model);
    }

    @Test
    @DisplayName("getDefaultModel 空 preset 返回默认模型")
    void getDefaultModelBlankPresetReturnsDefault() {
        AgentDefaultModelProvider provider = new AgentDefaultModelProvider("gpt-4o");

        String model = provider.getDefaultModel("");

        assertEquals("gpt-4o", model);
    }

    @Test
    @DisplayName("register 多个 preset 各自返回对应模型")
    void registerMultiplePresets() {
        AgentDefaultModelProvider provider = new AgentDefaultModelProvider("gpt-4o");
        provider.register("coder", "claude-3.5-sonnet");
        provider.register("researcher", "gemini-pro");

        assertEquals("claude-3.5-sonnet", provider.getDefaultModel("coder"));
        assertEquals("gemini-pro", provider.getDefaultModel("researcher"));
        assertEquals("gpt-4o", provider.getDefaultModel("other"));
    }

    @Test
    @DisplayName("register null preset 抛 IllegalArgumentException")
    void registerNullPresetThrows() {
        AgentDefaultModelProvider provider = new AgentDefaultModelProvider("gpt-4o");

        assertThrows(IllegalArgumentException.class, () -> provider.register(null, "model"));
    }

    @Test
    @DisplayName("register 空 preset 抛 IllegalArgumentException")
    void registerBlankPresetThrows() {
        AgentDefaultModelProvider provider = new AgentDefaultModelProvider("gpt-4o");

        assertThrows(IllegalArgumentException.class, () -> provider.register("", "model"));
    }

    @Test
    @DisplayName("register null modelId 抛 IllegalArgumentException")
    void registerNullModelIdThrows() {
        AgentDefaultModelProvider provider = new AgentDefaultModelProvider("gpt-4o");

        assertThrows(IllegalArgumentException.class, () -> provider.register("coder", null));
    }

    @Test
    @DisplayName("register 空 modelId 抛 IllegalArgumentException")
    void registerBlankModelIdThrows() {
        AgentDefaultModelProvider provider = new AgentDefaultModelProvider("gpt-4o");

        assertThrows(IllegalArgumentException.class, () -> provider.register("coder", ""));
    }

    @Test
    @DisplayName("构造函数 null defaultModel 抛 NullPointerException")
    void nullDefaultModelThrows() {
        assertThrows(NullPointerException.class, () -> new AgentDefaultModelProvider(null));
    }

    @Test
    @DisplayName("getDefaultModel 无参返回默认模型")
    void getDefaultModelNoArgsReturnsDefault() {
        AgentDefaultModelProvider provider = new AgentDefaultModelProvider("gpt-4o");

        assertEquals("gpt-4o", provider.getDefaultModel());
    }
}
