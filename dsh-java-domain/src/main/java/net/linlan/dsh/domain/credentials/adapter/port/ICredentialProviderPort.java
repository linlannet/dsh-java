package net.linlan.dsh.domain.credentials.adapter.port;

import net.linlan.dsh.domain.credentials.model.valobj.CredentialInfoVO;
import net.linlan.dsh.domain.credentials.model.valobj.CredentialRef;
import net.linlan.dsh.domain.credentials.model.valobj.ResolvedCredentialVO;
import java.util.Optional;

/**
 * ICredentialProviderPort 定义ICredentialProvider提供方的领域端口，由基础设施层适配。
 */
public interface ICredentialProviderPort {

    /**
     * 处理resolve。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param ref ref。
     * @return 领域处理结果。
     */
    Optional<ResolvedCredentialVO> resolve(CredentialRef ref);

    /**
     * 处理describe。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param ref ref。
     * @return 领域处理结果。
     */
    CredentialInfoVO describe(CredentialRef ref);

    /**
     * 处理set。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param ref ref。
     * @return 领域处理结果。
     */
    void set(CredentialRef ref, String value);

    /**
     * 处理unset。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param ref ref。
     * @return 领域处理结果。
     */
    void unset(CredentialRef ref);
}
