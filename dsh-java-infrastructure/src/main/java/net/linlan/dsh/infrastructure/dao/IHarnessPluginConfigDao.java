package net.linlan.dsh.infrastructure.dao;

import net.linlan.dsh.infrastructure.dao.po.HarnessPluginConfigPO;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface IHarnessPluginConfigDao {

    @Insert("""
            INSERT INTO harness_plugin_config
                (plugin_id, config_key, config_value, config_layer, updated_at)
            VALUES
                (#{pluginId}, #{configKey}, #{configValue}, #{configLayer}, #{updatedAt})
            ON DUPLICATE KEY UPDATE
                config_value = VALUES(config_value),
                updated_at = VALUES(updated_at)
            """)
    void save(HarnessPluginConfigPO configPO);

    @Select("""
            SELECT plugin_id, config_key, config_value, config_layer, updated_at
            FROM harness_plugin_config
            WHERE plugin_id = #{pluginId}
              AND config_key = #{configKey}
              AND config_layer IN ('DEFAULT', 'OVERRIDE')
            ORDER BY CASE config_layer WHEN 'OVERRIDE' THEN 0 ELSE 1 END
            """)
    List<HarnessPluginConfigPO> findEffective(@Param("pluginId") String pluginId,
                                              @Param("configKey") String configKey);

    @Select("""
            SELECT plugin_id, config_key, config_value, config_layer, updated_at
            FROM harness_plugin_config
            WHERE plugin_id = #{pluginId}
            ORDER BY config_layer DESC, config_key ASC
            """)
    List<HarnessPluginConfigPO> listByPluginId(@Param("pluginId") String pluginId);

    @Delete("""
            DELETE FROM harness_plugin_config
            WHERE plugin_id = #{pluginId}
              AND config_layer = #{configLayer}
            """)
    void deleteByPluginIdAndLayer(@Param("pluginId") String pluginId,
                                  @Param("configLayer") String configLayer);

    @Delete("""
            DELETE FROM harness_plugin_config
            WHERE plugin_id = #{pluginId}
              AND config_key = #{configKey}
              AND config_layer = #{configLayer}
            """)
    void deleteByPluginIdAndKeyAndLayer(@Param("pluginId") String pluginId,
                                        @Param("configKey") String configKey,
                                        @Param("configLayer") String configLayer);
}
