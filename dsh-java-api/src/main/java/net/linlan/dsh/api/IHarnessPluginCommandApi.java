package net.linlan.dsh.api;

import net.linlan.dsh.api.dto.ActivatePluginRequestDTO;
import net.linlan.dsh.api.dto.ActivatePluginResponseDTO;
import net.linlan.dsh.api.dto.InstallPluginRequestDTO;
import net.linlan.dsh.api.dto.InstallPluginResponseDTO;
import net.linlan.dsh.api.dto.PluginLifecycleResponseDTO;
import net.linlan.dsh.api.dto.RunPluginRequestDTO;
import net.linlan.dsh.api.dto.RunPluginResponseDTO;
import net.linlan.dsh.api.response.Response;

public interface IHarnessPluginCommandApi {

    Response<InstallPluginResponseDTO> installPlugin(InstallPluginRequestDTO request);

    Response<ActivatePluginResponseDTO> activatePlugin(ActivatePluginRequestDTO request);

    Response<RunPluginResponseDTO> runPlugin(RunPluginRequestDTO request);

    Response<PluginLifecycleResponseDTO> disablePlugin(String pluginId);

    Response<PluginLifecycleResponseDTO> enablePlugin(String pluginId);

    Response<PluginLifecycleResponseDTO> uninstallPlugin(String pluginId);
}
