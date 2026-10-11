package net.linlan.dsh.api;

import net.linlan.dsh.api.dto.InstalledPluginResponseDTO;
import net.linlan.dsh.api.response.Response;
import java.util.List;

public interface IHarnessInstalledPluginQueryApi {

    Response<List<InstalledPluginResponseDTO>> listInstalledPlugins();
}
