package net.linlan.dsh.api;

import net.linlan.dsh.api.dto.InstalledPluginResponseDTO;
import net.linlan.dsh.api.dto.PluginInventoryResponseDTO;
import net.linlan.dsh.api.dto.QueryPluginStatusResponseDTO;
import net.linlan.dsh.api.response.Response;
import java.util.List;

public interface IHarnessPluginQueryApi {

    Response<List<InstalledPluginResponseDTO>> listInstalledPlugins();

    Response<PluginInventoryResponseDTO> listPluginInventory();

    Response<QueryPluginStatusResponseDTO> queryPluginStatus(String pluginId);
}
