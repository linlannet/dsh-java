package net.linlan.dsh.domain.plugin.registry.adapter.port;

import net.linlan.dsh.domain.plugin.registry.model.entity.HarnessPluginEntity;
import net.linlan.dsh.domain.plugin.registry.model.valobj.PluginInventoryMetadataVO;
import java.util.Optional;

public interface IPluginInventoryMetadataPort {

    Optional<PluginInventoryMetadataVO> read(HarnessPluginEntity plugin);
}
