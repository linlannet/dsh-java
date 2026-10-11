package net.linlan.dsh.domain.credentials;

import net.linlan.dsh.domain.credentials.model.valobj.CredentialInfoVO;
import net.linlan.dsh.domain.credentials.model.valobj.CredentialRef;
import net.linlan.dsh.domain.credentials.model.valobj.ResolvedCredentialVO;
import java.util.Optional;

/**
 * 凭据服务接口。
 * <p>
 * 执行流程：
 * 1. 上层传入凭据引用；
 * 2. 解析或写入真实密钥；
 * 3. 返回密钥内容或元信息给调用方。
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
public interface ICredentialService {

    /**
     * 解析凭据内容。
     * <p>
     * 执行流程：
     * 1. 读取凭据引用；
     * 2. 查找真实密钥值；
     * 3. 返回解析结果。
     */
    Optional<ResolvedCredentialVO> resolve(CredentialRef ref);

    /**
     * 描述一个凭据引用。
     * <p>
     * 执行流程：
     * 1. 读取凭据引用；
     * 2. 查询元信息；
     * 3. 返回描述对象。
     */
    CredentialInfoVO describe(CredentialRef ref);

    /**
     * 设置凭据值。
     * <p>
     * 执行流程：
     * 1. 接收凭据引用和明文值；
     * 2. 持久化到对应存储；
     * 3. 完成更新。
     */
    void set(CredentialRef ref, String value);

    /**
     * 删除凭据值。
     * <p>
     * 执行流程：
     * 1. 读取凭据引用；
     * 2. 清理对应存储；
     * 3. 返回删除完成信号。
     */
    void unset(CredentialRef ref);
}
