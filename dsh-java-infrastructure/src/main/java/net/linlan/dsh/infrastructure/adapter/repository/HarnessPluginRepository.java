package net.linlan.dsh.infrastructure.adapter.repository;

import net.linlan.dsh.domain.plugin.registry.adapter.repository.IHarnessPluginRepository;
import net.linlan.dsh.domain.plugin.registry.model.entity.HarnessPluginEntity;
import net.linlan.dsh.domain.plugin.registry.model.valobj.PluginInstallModeEnumVO;
import net.linlan.dsh.domain.plugin.registry.model.valobj.PluginInstallStatusEnumVO;
import net.linlan.dsh.domain.plugin.registry.model.valobj.PluginRuntimeTypeEnumVO;
import net.linlan.dsh.infrastructure.dao.IHarnessPluginDao;
import net.linlan.dsh.infrastructure.dao.po.HarnessPluginPO;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * 通过 DAO 层持久化插件安装信息。
 *
 * <p>调用流程：领域端口 → 基础设施适配器 → 外部系统。</p>
 * <p>示例：适配器只实现端口契约，业务规则保留在 domain。</p>
 *
 */
@Repository
public class HarnessPluginRepository implements IHarnessPluginRepository {

    private final IHarnessPluginDao harnessPluginDao;

    /**
     * 处理Harness 插件 Repository。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param harnessPluginDao harness 插件 Dao参数。
     * @return 方法执行结果。
     */
    public HarnessPluginRepository(IHarnessPluginDao harnessPluginDao) {
        this.harnessPluginDao = harnessPluginDao;
    }

    @Override
    @Transactional
    public void save(HarnessPluginEntity plugin) {
        harnessPluginDao.save(new HarnessPluginPO(
                plugin.getPluginId(),
                plugin.getDisplayName(),
                plugin.getPluginVersion(),
                plugin.getRuntimeType().name(),
                plugin.getInstallMode().name(),
                plugin.getSourcePath(),
                plugin.getEntrypoint(),
                plugin.getStatus().name(),
                plugin.getInstalledAt()
        ));
    }
    /**
     * 查找By Plugin Id。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param pluginId 插件 ID。
     * @return 方法执行结果。
     */


    @Override
    public Optional<HarnessPluginEntity> findByPluginId(String pluginId) {
        return harnessPluginDao.findByPluginId(pluginId).map(this::toEntity);
    }
    /**
     * 列出All。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @return 方法执行结果。
     */


    @Override
    public List<HarnessPluginEntity> listAll() {
        return harnessPluginDao.listAll().stream().map(this::toEntity).toList();
    }
    /**
     * 删除By Plugin Id。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param pluginId 插件 ID。
     */


    @Override
    @Transactional
    public void deleteByPluginId(String pluginId) {
        harnessPluginDao.deleteByPluginId(pluginId);
    }

    /**
     * 转换为 Entity。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param pluginPO 插件 P O参数。
     * @return 转换后的目标对象。
     */
    private HarnessPluginEntity toEntity(HarnessPluginPO pluginPO) {
        return new HarnessPluginEntity(
                pluginPO.pluginId(),
                pluginPO.displayName(),
                pluginPO.pluginVersion(),
                PluginRuntimeTypeEnumVO.valueOf(pluginPO.runtimeType()),
                PluginInstallModeEnumVO.valueOf(pluginPO.installMode()),
                pluginPO.sourcePath(),
                pluginPO.entrypoint(),
                PluginInstallStatusEnumVO.valueOf(pluginPO.status()),
                pluginPO.installedAt()
        );
    }
}
