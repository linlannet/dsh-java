package net.linlan.dsh.domain.skill;

import net.linlan.dsh.domain.skill.model.entity.SkillDefinition;
import net.linlan.dsh.domain.skill.model.entity.SkillSummary;
import java.util.List;
import java.util.Optional;

/**
 * Application-facing skill seam. Merges provider catalogs, resolves the winning
 * skill for a name, and exposes summaries and definitions to consumers.
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
public interface ISkillService {

    /**
     * 处理list。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param cwd 工作目录。
     * @return 领域处理结果。
     */
    List<SkillSummary> list(String cwd);

    /**
     * 处理get。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param name 名称。
     * @return 领域处理结果。
     */
    Optional<SkillDefinition> get(String name, String cwd);
}
