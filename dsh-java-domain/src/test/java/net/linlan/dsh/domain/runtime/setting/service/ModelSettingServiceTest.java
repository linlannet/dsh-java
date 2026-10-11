package net.linlan.dsh.domain.runtime.setting.service;

import net.linlan.dsh.domain.runtime.setting.adapter.repository.IModelSettingRepository;
import net.linlan.dsh.domain.runtime.setting.model.entity.ModelSettingEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class ModelSettingServiceTest {

    @Test
    void firstEnabledChannelBecomesActive() {
        var repository = new InMemoryModelSettingRepository();
        var service = new ModelSettingService(repository);

        var saved = service.save("deepseek", "deepseek", "deepseek", "deepseek-chat",
                "http://deepseek.example/v1", "key", "openai", true);

        assertTrue(saved.active());
        assertEquals("deepseek", service.findActive().orElseThrow().channelCode());
    }

    @Test
    void addingAnotherChannelDoesNotReplaceActiveChannel() {
        var repository = new InMemoryModelSettingRepository();
        var service = new ModelSettingService(repository);
        service.save("deepseek", "deepseek", "deepseek", "deepseek-chat", null, null, "openai", true);

        var saved = service.save("qwen", "qwen", "qwen", "qwen-max", null, null, "openai", true);

        assertFalse(saved.active());
        assertEquals("deepseek", service.findActive().orElseThrow().channelCode());
    }

    @Test
    void activatingChannelMovesGlobalActiveMarker() {
        var repository = new InMemoryModelSettingRepository();
        var service = new ModelSettingService(repository);
        service.save("deepseek", "deepseek", "deepseek", "deepseek-chat", null, null, "openai", true);
        service.save("qwen", "qwen", "qwen", "qwen-max", null, null, "openai", true);

        var activated = service.activate("qwen");

        assertTrue(activated.active());
        assertEquals("qwen", service.findActive().orElseThrow().channelCode());
        assertFalse(service.findByChannelCode("deepseek").orElseThrow().active());
    }

    @Test
    void disabledChannelCannotBeActivated() {
        var repository = new InMemoryModelSettingRepository();
        var service = new ModelSettingService(repository);
        service.save("deepseek", "deepseek", "deepseek", "deepseek-chat", null, null, "openai", true);
        service.save("qwen", "qwen", "qwen", "qwen-max", null, null, "openai", false);

        assertThrows(IllegalArgumentException.class, () -> service.activate("qwen"));
        assertEquals("deepseek", service.findActive().orElseThrow().channelCode());
    }

    @Test
    void disablingActiveChannelSelectsAnotherEnabledChannel() {
        var repository = new InMemoryModelSettingRepository();
        var service = new ModelSettingService(repository);
        service.save("deepseek", "deepseek", "deepseek", "deepseek-chat", null, null, "openai", true);
        service.save("qwen", "qwen", "qwen", "qwen-max", null, null, "openai", true);

        var saved = service.save("deepseek", "deepseek", "deepseek", "deepseek-chat", null, null, "openai", false);

        assertFalse(saved.enabled());
        assertEquals("qwen", service.findActive().orElseThrow().channelCode());
    }

    @Test
    void deletingActiveChannelSelectsAnotherEnabledChannel() {
        var repository = new InMemoryModelSettingRepository();
        var service = new ModelSettingService(repository);
        service.save("deepseek", "deepseek", "deepseek", "deepseek-chat", null, null, "openai", true);
        service.save("qwen", "qwen", "qwen", "qwen-max", null, null, "openai", true);

        service.deleteByChannelCode("deepseek");

        assertEquals("qwen", service.findActive().orElseThrow().channelCode());
        assertTrue(service.findByChannelCode("deepseek").isEmpty());
    }

    @Test
    void reEnablingOnlyChannelMakesItActiveAgain() {
        var repository = new InMemoryModelSettingRepository();
        var service = new ModelSettingService(repository);
        service.save("deepseek", "deepseek", "deepseek", "deepseek-chat", null, null, "openai", true);
        service.save("deepseek", "deepseek", "deepseek", "deepseek-chat", null, null, "openai", false);

        var saved = service.save("deepseek", "deepseek", "deepseek", "deepseek-chat", null, null, "openai", true);

        assertTrue(saved.active());
        assertEquals("deepseek", service.findActive().orElseThrow().channelCode());
    }

    private static final class InMemoryModelSettingRepository implements IModelSettingRepository {

        private final List<ModelSettingEntity> settings = new ArrayList<>();

        @Override
        public void save(ModelSettingEntity setting) {
            if (setting.isActive()) {
                settings.replaceAll(existing -> copy(existing, false));
            }
            settings.removeIf(existing -> existing.getChannelCode().equals(setting.getChannelCode()));
            settings.add(setting);
        }

        @Override
        public List<ModelSettingEntity> queryAll() {
            return List.copyOf(settings);
        }

        @Override
        public Optional<ModelSettingEntity> findByChannelCode(String channelCode) {
            return settings.stream()
                    .filter(setting -> setting.getChannelCode().equals(channelCode))
                    .findFirst();
        }

        @Override
        public Optional<ModelSettingEntity> findActive() {
            return settings.stream().filter(ModelSettingEntity::isActive).findFirst();
        }

        @Override
        public boolean activate(String channelCode) {
            Optional<ModelSettingEntity> target = findByChannelCode(channelCode);
            if (target.isEmpty()) {
                return false;
            }
            settings.replaceAll(existing -> copy(existing, existing.getChannelCode().equals(channelCode)));
            return true;
        }

        @Override
        public void deleteByChannelCode(String channelCode) {
            settings.removeIf(setting -> setting.getChannelCode().equals(channelCode));
        }

        private ModelSettingEntity copy(ModelSettingEntity setting, boolean active) {
            return new ModelSettingEntity(
                    setting.getChannelCode(), setting.getDisplayName(), setting.getProviderCode(), setting.getModelCode(),
                    setting.getBaseUrl(), setting.getApiKeyRef(), setting.getProtocol(),
                    setting.isEnabled(), active, setting.getUpdatedAt());
        }
    }
}
