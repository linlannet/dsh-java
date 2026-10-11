package net.linlan.dsh.cases.plugin;

import net.linlan.dsh.api.dto.InstalledPluginResponseDTO;

import java.util.List;

/**
 * 已安装插件查询用例契约。
 * <p>流程：Trigger/前端 → 本接口 → 查询 Pipeline → 插件仓储 → DTO 列表。</p>
 * <p>数据示例：无入参；返回包含 {@code pluginId=weather-forecast, status=ENABLED, runtimeType=PYTHON} 的列表。</p>
 */
public interface IInstalledPluginQueryCase {
    List<InstalledPluginResponseDTO> listInstalledPlugins();
}
