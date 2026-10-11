package net.linlan.dsh.domain.storage.service;

import net.linlan.dsh.domain.storage.IStorageService;
import net.linlan.dsh.domain.storage.adapter.port.IStorageBackendPort;
import net.linlan.dsh.domain.storage.model.valobj.KvSnapshot;
import net.linlan.dsh.domain.storage.model.valobj.KvUnitDescriptor;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Delegates to a configured {@link IStorageBackendPort}; the port owns all
 * physical-medium semantics (file layout, atomicity, permissions).
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
@Service
public class StorageService implements IStorageService {

    private final IStorageBackendPort backendPort;

    /**
     * 处理Storage Service。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param backendPort backend Port参数。
     * @return 方法执行结果。
     */
    public StorageService(IStorageBackendPort backendPort) {
        this.backendPort = backendPort;
    }
    /**
     * 加载。
     */

    @Override
    public KvSnapshot load(KvUnitDescriptor descriptor) {
        return backendPort.load(descriptor);
    /**
     * 获取。
     */
    }

    @Override
    public Optional<String> get(KvUnitDescriptor descriptor, String table, String key) {
        KvSnapshot snapshot = backendPort.load(descriptor);
        Map<String, String> tableData = snapshot.tables().get(table);
        if (tableData == null) return Optional.empty();
        return Optional.ofNullable(tableData.get(key));
    }

    @Override
    public void put(KvUnitDescriptor descriptor, String table, String key, String value) {
        backendPort.put(descriptor, table, key, value);
    }
    /**
     * 移除目标对象。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param descriptor descriptor参数。
     * @param table table参数。
     * @param key key参数。
     */


    @Override
    public void remove(KvUnitDescriptor descriptor, String table, String key) {
        backendPort.remove(descriptor, table, key);
    }
    /**
     * put All。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param descriptor descriptor参数。
     * @param Map<String Map String参数。
     * @param Map<String Map String参数。
     * @param puts puts参数。
     */


    @Override
    public void putAll(KvUnitDescriptor descriptor, Map<String, Map<String, String>> puts) {
        backendPort.putAll(descriptor, puts);
    }
}
