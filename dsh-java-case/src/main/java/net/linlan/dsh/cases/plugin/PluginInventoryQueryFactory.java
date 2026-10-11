package net.linlan.dsh.cases.plugin;

import net.linlan.dsh.api.dto.PluginInventoryItemDTO;
import net.linlan.dsh.api.dto.PluginInventoryResponseDTO;
import net.linlan.dsh.domain.plugin.registry.model.valobj.PluginInventoryItemVO;
import net.linlan.dsh.domain.plugin.registry.service.PluginInventoryService;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PluginInventoryQueryFactory {

    private final PluginInventoryService pluginInventoryService;

    public PluginInventoryQueryFactory(PluginInventoryService pluginInventoryService) {
        this.pluginInventoryService = pluginInventoryService;
    }

    public PluginInventoryResponseDTO inventory() {
        List<PluginInventoryItemDTO> plugins = pluginInventoryService.inventory().stream()
                .map(PluginInventoryQueryFactory::toDTO)
                .toList();
        return new PluginInventoryResponseDTO(Instant.now(), plugins.size(), plugins);
    }

    private static PluginInventoryItemDTO toDTO(PluginInventoryItemVO item) {
        return new PluginInventoryItemDTO(
                item.pluginId(),
                item.displayName(),
                item.pluginVersion(),
                item.runtimeType(),
                item.installMode(),
                item.status(),
                item.sourcePath(),
                item.entrypoint(),
                item.manifestPath(),
                item.author(),
                item.description(),
                item.scope()
        );
    }
}
