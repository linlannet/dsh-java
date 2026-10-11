package net.linlan.dsh.domain.plugin.registry.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.linlan.dsh.domain.plugin.registry.adapter.repository.IHarnessPluginRepository;
import net.linlan.dsh.domain.plugin.registry.adapter.port.IPluginInventoryMetadataPort;
import net.linlan.dsh.domain.plugin.registry.model.entity.HarnessPluginEntity;
import net.linlan.dsh.domain.plugin.registry.model.valobj.PluginInstallModeEnumVO;
import net.linlan.dsh.domain.plugin.registry.model.valobj.PluginInstallStatusEnumVO;
import net.linlan.dsh.domain.plugin.registry.model.valobj.PluginInventoryMetadataVO;
import net.linlan.dsh.domain.plugin.registry.model.valobj.PluginInventoryItemVO;
import net.linlan.dsh.domain.plugin.registry.model.valobj.PluginRuntimeTypeEnumVO;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PluginInventoryServiceTest {

    @Test
    void shouldSortAndMapInventory() {
        HarnessPluginEntity plugin = plugin("zeta");
        HarnessPluginEntity other = plugin("alpha");
        IHarnessPluginRepository repository = new IHarnessPluginRepository() {
            @Override public void save(HarnessPluginEntity plugin) {}
            @Override public Optional<HarnessPluginEntity> findByPluginId(String pluginId) { return Optional.empty(); }
            @Override public List<HarnessPluginEntity> listAll() { return List.of(plugin, other); }
            @Override public void deleteByPluginId(String pluginId) {}
        };
        var metadataPort = new IPluginInventoryMetadataPort() {
            @Override public Optional<PluginInventoryMetadataVO> read(HarnessPluginEntity plugin) {
                return Optional.of(new PluginInventoryMetadataVO("/tmp", "Alice", "Test", "project"));
            }
        };
        PluginInventoryService service = new PluginInventoryService(repository, metadataPort);

        List<PluginInventoryItemVO> result = service.inventory();

        assertEquals(List.of("alpha", "zeta"), result.stream().map(PluginInventoryItemVO::pluginId).toList());
        assertEquals("project", result.get(0).scope());
    }

    private HarnessPluginEntity plugin(String pluginId) {
        return new HarnessPluginEntity(
                pluginId,
                pluginId,
                "1.0.0",
                PluginRuntimeTypeEnumVO.JAVA_NATIVE,
                PluginInstallModeEnumVO.NATIVE,
                "/tmp",
                "plugin.jar",
                PluginInstallStatusEnumVO.ACTIVE,
                Instant.EPOCH
        );
    }
}
