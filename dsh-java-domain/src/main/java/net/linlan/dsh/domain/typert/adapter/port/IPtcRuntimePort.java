package net.linlan.dsh.domain.typert.adapter.port;

import java.util.Map;

/**
 * IPtcRuntimePort 定义 PTC 运行时的领域端口，由基础设施层适配。
 *
 * <p>调用流程：领域端口 → 基础设施适配器 → 外部系统。</p>
 * <p>示例：适配器只实现端口契约，业务规则保留在 domain。</p>
 *
 */
public interface IPtcRuntimePort {

    /**
     * 执行 PTC 脚本。
     *
     * @param script 脚本内容
     * @param env    环境变量
     * @return 执行输出
     */
    String execute(String script, Map<String, Object> env);
}
