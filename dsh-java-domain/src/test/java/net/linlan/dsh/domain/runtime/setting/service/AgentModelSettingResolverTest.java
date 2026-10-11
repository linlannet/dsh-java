package net.linlan.dsh.domain.runtime.setting.service;

import net.linlan.dsh.domain.agent.model.entity.AgentOptions;
import net.linlan.dsh.domain.runtime.setting.IModelSettingQueryService;
import net.linlan.dsh.domain.runtime.setting.model.valobj.ModelSettingVO;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class AgentModelSettingResolverTest {

    @Test
    void usesActiveChannelWhenRequestHasNoChannel() {
        var settings = new SettingsService(Map.of(
                "deepseek", setting("deepseek", "deepseek", "Deepseek-V4.1-Flash",
                        "http://db.example/v1", "db-key", true)
        ));
        var resolver = resolver(settings);

        AgentOptions options = resolver.resolve(null, null);

        assertEquals("deepseek", options.channelCode());
        assertEquals("deepseek", options.provider());
        assertEquals("Deepseek-V4.1-Flash", options.model());
        assertEquals("http://db.example/v1", options.baseUrl());
        assertEquals("db-key", options.apiKey());
    }

    @Test
    void usesRequestedChannelWhenProvided() {
        var settings = new SettingsService(Map.of(
                "deepseek", setting("deepseek", "deepseek", "Deepseek-V4.1-Flash",
                        "http://default.example/v1", "default-key", true),
                "qwen", setting("qwen", "qwen", "qwen-max",
                        "http://qwen.example/v1", "qwen-key", false)
        ));
        var resolver = resolver(settings);

        AgentOptions options = resolver.resolve("qwen", null);

        assertEquals("qwen", options.channelCode());
        assertEquals("qwen", options.provider());
        assertEquals("qwen-max", options.model());
        assertEquals("http://qwen.example/v1", options.baseUrl());
        assertEquals("qwen-key", options.apiKey());
    }

    @Test
    void fallsBackToFileModelWhenDatabaseIsEmpty() {
        var resolver = resolver(new SettingsService(Map.of()));

        AgentOptions options = resolver.resolve(null, null);

        assertEquals("default", options.channelCode());
        assertEquals("custom", options.provider());
        assertEquals("file-model", options.model());
        assertEquals(8192, options.maxTokens());
    }

    @Test
    void requestedChannelFallsBackWhenMissing() {
        var settings = new SettingsService(Map.of(
                "deepseek", setting("deepseek", "deepseek", "Deepseek-V4.1-Flash",
                        "http://db.example/v1", "db-key", true)
        ));
        var resolver = resolver(settings);

        AgentOptions options = resolver.resolve("missing", null);

        assertEquals("default", options.channelCode());
        assertEquals("custom", options.provider());
        assertEquals("file-model", options.model());
    }

    private AgentModelSettingResolver resolver(IModelSettingQueryService settings) {
        return new AgentModelSettingResolver(settings, "default", "file-model", "http://file.example/v1", "file-key");
    }

    private ModelSettingVO setting(String channelCode, String providerCode, String modelCode,
                                   String baseUrl, String apiKeyRef, boolean active) {
        return new ModelSettingVO(channelCode, channelCode, providerCode, modelCode, baseUrl, apiKeyRef,
                "openai", true, active, Instant.parse("2026-01-01T00:00:00Z"));
    }

    private static final class SettingsService implements IModelSettingQueryService {
        private final Map<String, ModelSettingVO> settings;

        private SettingsService(Map<String, ModelSettingVO> settings) {
            this.settings = new HashMap<>(settings);
        }

        @Override
        public List<ModelSettingVO> queryAll() {
            return List.copyOf(settings.values());
        }

        @Override
        public Optional<ModelSettingVO> findByChannelCode(String channelCode) {
            return Optional.ofNullable(settings.get(channelCode));
        }

        @Override
        public Optional<ModelSettingVO> findActive() {
            return settings.values().stream().filter(ModelSettingVO::active).findFirst();
        }
    }
}
