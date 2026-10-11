package net.linlan.dsh.domain.typert.adapter.port;

import java.util.List;
import java.util.Optional;

/**
 * Runtime registry for Host object lookup providers.
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
public interface ITypertLookupRegistry {

    void register(String key, Object provider);

    Optional<Object> get(String key);

    List<String> keys();
}
