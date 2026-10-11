package net.linlan.dsh.infrastructure.dao;

import net.linlan.dsh.infrastructure.dao.po.HarnessPluginRuntimeBindingPO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

/**
 * 定义插件桥接计划的底层持久化访问。
 *
 * <p>调用流程：领域端口 → 基础设施适配器 → 外部系统。</p>
 * <p>示例：适配器只实现端口契约，业务规则保留在 domain。</p>
 *
 */
@Mapper
public interface IHarnessPluginRuntimeBindingDao {

    /**
     * 保存某插件最近一次桥接解析结果。
     *
     * @param bindingPO 绑定记录
     */
    @Insert("""
            INSERT INTO harness_plugin_runtime_binding
                (plugin_id, package_type, manifest_path, working_directory, runtime_command, bridge_ready, message, checked_at)
            VALUES
                (#{pluginId}, #{packageType}, #{manifestPath}, #{workingDirectory}, #{runtimeCommand}, #{bridgeReady}, #{message}, #{checkedAt})
            ON DUPLICATE KEY UPDATE
                package_type = VALUES(package_type),
                manifest_path = VALUES(manifest_path),
                working_directory = VALUES(working_directory),
                runtime_command = VALUES(runtime_command),
                bridge_ready = VALUES(bridge_ready),
                message = VALUES(message),
                checked_at = VALUES(checked_at)
            """)
    void save(HarnessPluginRuntimeBindingPO bindingPO);
}
