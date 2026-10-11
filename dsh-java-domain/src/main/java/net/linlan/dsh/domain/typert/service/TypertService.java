package net.linlan.dsh.domain.typert.service;

import net.linlan.dsh.domain.typert.ITypertService;
import net.linlan.dsh.domain.typert.adapter.port.ITypertRegistryContract;
import net.linlan.dsh.domain.typert.model.entity.InvocationDescriptor;
import net.linlan.dsh.domain.typert.model.entity.TypertContribution;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Typert service: owns the registry contract and manages contributions.
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
@Service
public class TypertService implements ITypertService {

    private final ITypertRegistryContract registryContract;

    /**
     * 处理Typert Service。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param registryContract registry Contract参数。
     * @return 方法执行结果。
     */
    public TypertService(ITypertRegistryContract registryContract) {
        this.registryContract = registryContract;
    }

    @Override
    public void registerContribution(TypertContribution contribution) {
        for (InvocationDescriptor desc : contribution.invocations()) {
            registryContract.remotes().register(desc);
            registryContract.local().register(desc);
        }
    }
    /**
     * 解析Invocation。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param endpoint endpoint参数。
     * @return 方法执行结果。
     */


    @Override
    public Optional<InvocationDescriptor> resolveInvocation(String endpoint) {
        return registryContract.remotes().get(endpoint);
    }
    /**
     * 列出Invocations。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @return 方法执行结果。
     */


    @Override
    public List<InvocationDescriptor> listInvocations() {
        return registryContract.remotes().list();
    }
    /**
     * registry。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @return 方法执行结果。
     */


    @Override
    public ITypertRegistryContract registry() {
        return registryContract;
    }
}
