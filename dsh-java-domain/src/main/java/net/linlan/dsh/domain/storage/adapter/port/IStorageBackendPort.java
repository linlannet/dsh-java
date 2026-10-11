package net.linlan.dsh.domain.storage.adapter.port;

import net.linlan.dsh.domain.storage.model.valobj.KvSnapshot;
import net.linlan.dsh.domain.storage.model.valobj.KvUnitDescriptor;
import java.util.Map;

/**
 * IStorageBackendPort 定义IStorageBackend的领域端口，由基础设施层适配。
 */
public interface IStorageBackendPort {

    /**
     * 处理load。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param descriptor descriptor。
     * @return 领域处理结果。
     */
    KvSnapshot load(KvUnitDescriptor descriptor);

    /**
     * 处理put。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param descriptor descriptor。
     * @param key key。
     * @return 领域处理结果。
     */
    void put(KvUnitDescriptor descriptor, String table, String key, String value);

    /**
     * 处理remove。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param descriptor descriptor。
     * @param key key。
     * @return 领域处理结果。
     */
    void remove(KvUnitDescriptor descriptor, String table, String key);

    /**
     * 处理putAll。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param descriptor descriptor。
     * @param puts put列表。
     * @return 领域处理结果。
     */
    void putAll(KvUnitDescriptor descriptor, Map<String, Map<String, String>> puts);
}
