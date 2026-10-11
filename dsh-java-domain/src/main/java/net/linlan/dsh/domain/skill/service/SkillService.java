package net.linlan.dsh.domain.skill.service;

import net.linlan.dsh.domain.skill.ISkillService;
import net.linlan.dsh.domain.skill.adapter.port.ISkillProviderPort;
import net.linlan.dsh.domain.skill.model.entity.SkillCandidate;
import net.linlan.dsh.domain.skill.model.entity.SkillDefinition;
import net.linlan.dsh.domain.skill.model.entity.SkillSummary;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Skill registry: merges provider catalogs, resolves the winning skill for a
 * name (lowest rank wins), and exposes summaries and definitions.
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
@Service
public class SkillService implements ISkillService {

    private static final Pattern SKILL_NAME = Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");
    private final List<ISkillProviderPort> providers;
    private final Map<String, List<SkillCandidate>> cache = new ConcurrentHashMap<>();

    /**
     * 处理Skill Service。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param providers providers参数。
     * @return 方法执行结果。
     */
    public SkillService(List<ISkillProviderPort> providers) {
        this.providers = providers;
    }
    /**
     * 列出。
     */

    @Override
    public List<SkillSummary> list(String cwd) {
        List<SkillCandidate> all = discoverAll(cwd);
        // Merge: lowest rank wins per name
        Map<String, SkillCandidate> winners = new TreeMap<>();
        for (SkillCandidate c : all) {
            SkillCandidate existing = winners.get(c.name());
            if (existing == null || c.rank() < existing.rank()) {
                winners.put(c.name(), c);
            }
        }
        return winners.values().stream()
                .map(c -> new SkillSummary(c.name(), c.description(), c.whenToUse(),
                        c.invocation(), c.source(), c.provider(), c.path()))
                .toList();
    /**
     * 获取。
     */
    }

    @Override
    public Optional<SkillDefinition> get(String name, String cwd) {
        if (name == null || !SKILL_NAME.matcher(name).matches()) return Optional.empty();
        List<SkillCandidate> all = discoverAll(cwd);
        // Find the winning candidate for this name
        SkillCandidate winner = null;
        for (SkillCandidate c : all) {
            if (c.name().equals(name) && (winner == null || c.rank() < winner.rank())) {
                winner = c;
            }
        }
        if (winner == null) return Optional.empty();
        // Find the provider that produced this candidate
        for (ISkillProviderPort provider : providers) {
            Optional<SkillDefinition> def = provider.load(winner);
            if (def.isPresent()) return def;
        }
        return Optional.empty();
    }

    /**
     * 发现 All。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param cwd 工作目录。
     * @return 方法执行结果。
     */
    private List<SkillCandidate> discoverAll(String cwd) {
        List<SkillCandidate> all = new ArrayList<>();
        for (ISkillProviderPort provider : providers) {
            all.addAll(provider.discover(cwd));
        }
        return all;
    }
}
