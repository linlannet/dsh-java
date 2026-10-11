package net.linlan.dsh.domain.typert.adapter.port;

import net.linlan.dsh.domain.typert.model.entity.InvocationDescriptor;
import java.util.List;
import java.util.Optional;

/**
 * Local invocation registry: resolves endpoint strings to invocation descriptors.
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
public interface ITypertLocalRegistry {

    void register(InvocationDescriptor descriptor);

    Optional<InvocationDescriptor> get(String endpoint);

    List<InvocationDescriptor> list();
}
