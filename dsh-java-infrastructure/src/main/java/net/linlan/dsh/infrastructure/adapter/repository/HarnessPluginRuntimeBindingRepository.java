package net.linlan.dsh.infrastructure.adapter.repository;

import net.linlan.dsh.domain.plugin.bridge.adapter.repository.IHarnessPluginRuntimeBindingRepository;
import net.linlan.dsh.domain.plugin.bridge.model.valobj.PluginBridgePlanVO;
import net.linlan.dsh.infrastructure.dao.IHarnessPluginRuntimeBindingDao;
import net.linlan.dsh.infrastructure.dao.po.HarnessPluginRuntimeBindingPO;
import java.time.Instant;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * 持久化某插件最近一次桥接解析结果。
 *
 * <p>调用流程：领域端口 → 基础设施适配器 → 外部系统。</p>
 * <p>示例：适配器只实现端口契约，业务规则保留在 domain。</p>
 *
 */
@Repository
public class HarnessPluginRuntimeBindingRepository implements IHarnessPluginRuntimeBindingRepository {

    private final IHarnessPluginRuntimeBindingDao harnessPluginRuntimeBindingDao;

    /**
     * 处理Harness 插件 运行时 Binding Repository。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param harnessPluginRuntimeBindingDao harness 插件 运行时 Binding Dao参数。
     * @return 方法执行结果。
     */
    public HarnessPluginRuntimeBindingRepository(IHarnessPluginRuntimeBindingDao harnessPluginRuntimeBindingDao) {
        this.harnessPluginRuntimeBindingDao = harnessPluginRuntimeBindingDao;
    }

    @Override
    @Transactional
    public void save(String pluginId, PluginBridgePlanVO plan, Instant checkedAt) {
        harnessPluginRuntimeBindingDao.save(new HarnessPluginRuntimeBindingPO(
                pluginId,
                plan.packageType().name(),
                plan.manifestPath(),
                plan.workingDirectory(),
                plan.runtimeCommand(),
                plan.bridgeReady(),
                plan.message(),
                checkedAt
        ));
    }
}
