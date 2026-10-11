package net.linlan.dsh.domain.typert;

import net.linlan.dsh.domain.typert.adapter.port.ITypertRegistryContract;
import net.linlan.dsh.domain.typert.model.entity.InvocationDescriptor;
import net.linlan.dsh.domain.typert.model.entity.TypertContribution;
import java.util.List;
import java.util.Optional;

/**
 * Application-facing Typert seam: manages generated reflection, Remote
 * invocations, and lookup/context providers.
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
public interface ITypertService {

    void registerContribution(TypertContribution contribution);

    Optional<InvocationDescriptor> resolveInvocation(String endpoint);

    List<InvocationDescriptor> listInvocations();

    ITypertRegistryContract registry();
}
