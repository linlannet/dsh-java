package net.linlan.dsh.domain.credentials.service;

import net.linlan.dsh.domain.credentials.ICredentialService;
import net.linlan.dsh.domain.credentials.adapter.port.ICredentialProviderPort;
import net.linlan.dsh.domain.credentials.model.valobj.CredentialInfoVO;
import net.linlan.dsh.domain.credentials.model.valobj.CredentialRef;
import net.linlan.dsh.domain.credentials.model.valobj.ResolvedCredentialVO;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Delegates to a configured {@link ICredentialProviderPort}; the port owns all
 * source-layer resolution and storage semantics.
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
@Service
public class CredentialService implements ICredentialService {

    private final ICredentialProviderPort providerPort;

    /**
     * 处理凭证 Service。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param providerPort 模型提供方 Port参数。
     * @return 方法执行结果。
     */
    public CredentialService(ICredentialProviderPort providerPort) {
        this.providerPort = providerPort;
    }

    @Override
    public Optional<ResolvedCredentialVO> resolve(CredentialRef ref) {
        return providerPort.resolve(ref);
    }

    @Override
    public CredentialInfoVO describe(CredentialRef ref) {
    /**
     * 设置。
     */
        return providerPort.describe(ref);
    }

    @Override
    public void set(CredentialRef ref, String value) {
        providerPort.set(ref, value);
    }

    @Override
    public void unset(CredentialRef ref) {
        providerPort.unset(ref);
    }
}
