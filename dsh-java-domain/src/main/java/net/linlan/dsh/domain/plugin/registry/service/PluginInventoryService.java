package net.linlan.dsh.domain.plugin.registry.service;

import net.linlan.dsh.domain.plugin.registry.adapter.port.IPluginInventoryMetadataPort;
import net.linlan.dsh.domain.plugin.registry.adapter.repository.IHarnessPluginRepository;
import net.linlan.dsh.domain.plugin.registry.model.entity.HarnessPluginEntity;
import net.linlan.dsh.domain.plugin.registry.model.valobj.PluginInventoryItemVO;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class PluginInventoryService {

    private final IHarnessPluginRepository pluginRepository;
    private final IPluginInventoryMetadataPort metadataPort;

    public PluginInventoryService(
            IHarnessPluginRepository pluginRepository,
            IPluginInventoryMetadataPort metadataPort
    ) {
        this.pluginRepository = Objects.requireNonNull(pluginRepository, "pluginRepository");
        this.metadataPort = Objects.requireNonNull(metadataPort, "metadataPort");
    }

    public List<PluginInventoryItemVO> inventory() {
        return pluginRepository.listAll().stream()
                .sorted(Comparator.comparing(HarnessPluginEntity::getPluginId))
                .map(plugin -> PluginInventoryItemVO.from(
                        plugin,
                        metadataPort.read(plugin).orElse(null)
                ))
                .toList();
    }
}
