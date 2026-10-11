package net.linlan.dsh.domain.plugin.registry.adapter.repository;

import net.linlan.dsh.domain.plugin.registry.model.entity.HarnessPluginEntity;
import java.util.List;
import java.util.Optional;

/**
 * Defines durable access for installed plugins.
 *
 * <p>调用流程：插件安装/激活 → Bridge 转换 → Runtime 执行 → Hook/事件回传。</p>
 * <p>示例：Java SPI 插件注册工具后，Agent 可通过工具名调用其 run 方法。</p>
 *
 */
public interface IHarnessPluginRepository {

    void save(HarnessPluginEntity plugin);

    Optional<HarnessPluginEntity> findByPluginId(String pluginId);

    List<HarnessPluginEntity> listAll();

    /**
     * 处理deleteByPluginIdID。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param pluginId 插件ID。
     * @return 领域处理结果。
     */
    void deleteByPluginId(String pluginId);
}
