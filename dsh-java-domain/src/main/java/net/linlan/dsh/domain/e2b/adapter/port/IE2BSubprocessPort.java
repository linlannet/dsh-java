package net.linlan.dsh.domain.e2b.adapter.port;

import net.linlan.dsh.domain.e2b.model.valobj.E2BExecResult;

/**
 * Port for running subprocesses inside an E2B sandbox.
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
public interface IE2BSubprocessPort {

    /**
     * 处理run。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param sandboxId sandboxIdID。
     * @return 领域处理结果。
     */
    E2BExecResult run(String sandboxId, String command);

    /**
     * 处理runWithInput。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param sandboxId sandboxIdID。
     * @param stdin stdin。
     * @return 领域处理结果。
     */
    E2BExecResult runWithInput(String sandboxId, String command, String stdin);
}
