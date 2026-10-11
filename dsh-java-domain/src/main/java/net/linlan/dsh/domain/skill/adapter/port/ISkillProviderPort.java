package net.linlan.dsh.domain.skill.adapter.port;

import net.linlan.dsh.domain.skill.model.entity.SkillCandidate;
import net.linlan.dsh.domain.skill.model.entity.SkillDefinition;
import java.util.List;
import java.util.Optional;

/**
 * Port implemented by skill providers (e.g. filesystem provider).
 * Providers decide where skills come from; the registry merges catalogs.
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
public interface ISkillProviderPort {

    /**
     * 处理discover。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param cwd 工作目录。
     * @return 领域处理结果。
     */
    List<SkillCandidate> discover(String cwd);

    /**
     * 处理load。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param candidate candidate。
     * @return 领域处理结果。
     */
    Optional<SkillDefinition> load(SkillCandidate candidate);
}
