package net.linlan.dsh.domain.storage;

import net.linlan.dsh.domain.storage.model.valobj.KvSnapshot;
import net.linlan.dsh.domain.storage.model.valobj.KvUnitDescriptor;
import java.util.Map;
import java.util.Optional;

/**
 * Application-facing KV storage seam. Each unit is a named logical store with
 * typed tables; the backend owns the physical medium.
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
public interface IStorageService {

    KvSnapshot load(KvUnitDescriptor descriptor);

    Optional<String> get(KvUnitDescriptor descriptor, String table, String key);

    void put(KvUnitDescriptor descriptor, String table, String key, String value);

    void remove(KvUnitDescriptor descriptor, String table, String key);

    void putAll(KvUnitDescriptor descriptor, Map<String, Map<String, String>> puts);
}
