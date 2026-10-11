package net.linlan.dsh.infrastructure.dao;

import net.linlan.dsh.infrastructure.dao.po.HarnessPluginPO;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Delete;

/**
 * 定义已安装插件的底层持久化访问。
 *
 * <p>调用流程：领域端口 → 基础设施适配器 → 外部系统。</p>
 * <p>示例：适配器只实现端口契约，业务规则保留在 domain。</p>
 *
 */
@Mapper
public interface IHarnessPluginDao {

    /**
     * 保存一条已安装插件记录。
     *
     * @param pluginPO 插件记录
     */
    @Insert("""
            INSERT INTO harness_plugin_installation
                (plugin_id, display_name, plugin_version, runtime_type, install_mode, source_path, entrypoint, status, installed_at)
            VALUES
                (#{pluginId}, #{displayName}, #{pluginVersion}, #{runtimeType}, #{installMode}, #{sourcePath}, #{entrypoint}, #{status}, #{installedAt})
            ON DUPLICATE KEY UPDATE
                display_name = VALUES(display_name),
                plugin_version = VALUES(plugin_version),
                runtime_type = VALUES(runtime_type),
                install_mode = VALUES(install_mode),
                source_path = VALUES(source_path),
                entrypoint = VALUES(entrypoint),
                status = VALUES(status),
                installed_at = VALUES(installed_at)
            """)
    void save(HarnessPluginPO pluginPO);

    /**
     * 按插件 ID 查询一条已安装插件记录。
     *
     * @param pluginId 插件 ID
     * @return 命中时返回已存储记录
     */
    @Select("""
            SELECT plugin_id, display_name, plugin_version, runtime_type, install_mode, source_path, entrypoint, status, installed_at
            FROM harness_plugin_installation
            WHERE plugin_id = #{pluginId}
            """)
    Optional<HarnessPluginPO> findByPluginId(@Param("pluginId") String pluginId);

    @Select("""
            SELECT plugin_id, display_name, plugin_version, runtime_type, install_mode, source_path, entrypoint, status, installed_at
            FROM harness_plugin_installation
            ORDER BY installed_at DESC
            """)
    List<HarnessPluginPO> listAll();

    /**
     * 永久删除一条已安装插件记录。
     *
     * @param pluginId 插件 ID
     */
    @Delete("DELETE FROM harness_plugin_installation WHERE plugin_id = #{pluginId}")
    void deleteByPluginId(@Param("pluginId") String pluginId);
}
