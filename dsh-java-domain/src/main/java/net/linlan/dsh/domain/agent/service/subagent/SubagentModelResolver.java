package net.linlan.dsh.domain.agent.service.subagent;

import net.linlan.dsh.domain.agent.model.entity.AgentOptions;

/**
 * 子 agent 模型选择契约。
 * <p>每次委派调用时实时读取宿主当前模型设置，不缓存到 Session。</p>
 */
@FunctionalInterface
public interface SubagentModelResolver {

    /**
     * 解析子 agent 运行模型选项。
     *
     * @return 实时解析出的 AgentOptions；不得返回 null
     */
    AgentOptions resolve();
}
