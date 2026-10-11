package net.linlan.dsh.domain.e2b;

import net.linlan.dsh.domain.e2b.adapter.port.IE2BFsPort;
import net.linlan.dsh.domain.e2b.adapter.port.IE2BSubprocessPort;
import net.linlan.dsh.domain.e2b.model.valobj.E2BSandboxEnv;
import java.util.Optional;

/**
 * Application-facing E2B sandbox seam: create, manage, and access sandboxes.
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
public interface IE2BSandboxService {

    /**
     * 创建领域对象。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param env env。
     * @return 领域处理结果。
     */
    String create(E2BSandboxEnv env);

    /**
     * 处理f列表。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param sandboxId sandboxIdID。
     * @return 领域处理结果。
     */
    Optional<IE2BFsPort> fs(String sandboxId);

    /**
     * 处理subproces列表。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param sandboxId sandboxIdID。
     * @return 领域处理结果。
     */
    Optional<IE2BSubprocessPort> subprocess(String sandboxId);

    /**
     * 处理kill。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param sandboxId sandboxIdID。
     * @return 领域处理结果。
     */
    void kill(String sandboxId);
}
