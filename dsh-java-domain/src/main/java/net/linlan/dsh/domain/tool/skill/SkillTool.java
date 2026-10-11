package net.linlan.dsh.domain.tool.skill;

import net.linlan.dsh.domain.model.valobj.ContentBlock;
import net.linlan.dsh.domain.model.valobj.TextBlock;
import net.linlan.dsh.domain.skill.ISkillService;
import net.linlan.dsh.domain.skill.model.entity.SkillDefinition;
import net.linlan.dsh.domain.model.entity.ToolDefinition;
import net.linlan.dsh.domain.model.entity.ToolExecutionResult;
import net.linlan.dsh.domain.model.entity.ToolRunContext;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * {@code skill} 工具：按名称加载技能指令并注入会话上下文。
 * <p>
 * 执行流程：
 * 1. 模型给出技能名（kebab-case）；
 * 2. 经 {@link ISkillService} 解析出胜出候选并加载完整定义；
 * 3. 技能正文作为工具结果返回，同时 deferContext 一条指令消息供后续步骤遵循。
 * <p>
 * Mirrors TS {@code tool-skill}.
 *
 *
 */
public class SkillTool implements ToolDefinition {

    private static final String NAME = "skill";
    private static final String DESCRIPTION =
            "Load a skill's instruction set by name (kebab-case) and apply it to the current session. " +
            "Use when a task matches an installed skill's domain — the skill body tells you the exact workflow, " +
            "commands, and conventions to follow.";

    private final ISkillService skillService;
    private final String cwd;

    /**
     * 处理Skill 工具。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param skillService skill Service参数。
     * @param cwd 工作目录。
     * @return 方法执行结果。
     */
    public SkillTool(ISkillService skillService, String cwd) {
        this.skillService = skillService;
        this.cwd = cwd;
    }

    @Override public String name() { return NAME; }
    @Override public String description() { return DESCRIPTION; }

    @Override
    public Map<String, Object> parameters() {
        return Map.of(
                "type", "object",
                "additionalProperties", false,
                "properties", Map.of(
                        "name", Map.of(
                                "type", "string",
                                "description", "Skill name in kebab-case, e.g. pdf, find-skills, monorepo-setup."
                        )
                ),
                "required", List.of("name")
        );
    }
    /**
     * 返回 output Schema。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @return 方法执行结果。
     */


    @Override
    public Map<String, Object> outputSchema() {
        return Map.of(
                "type", "object",
                "additionalProperties", true,
                "properties", Map.of(
                        "name", Map.of("type", "string"),
                        "loaded", Map.of("type", "boolean")
                )
        );
    }

    @Override
    /**
     * 执行工具调用。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param args 启动参数。
     * @param ctx ctx参数。
     * @return 方法执行结果。
     */

    @SuppressWarnings("unchecked")
    public CompletableFuture<ToolExecutionResult> execute(Object args, ToolRunContext ctx) {
        Map<String, Object> argMap = args instanceof Map ? (Map<String, Object>) args : Map.of();
        String name = argMap.get("name") == null ? "" : String.valueOf(argMap.get("name")).trim();
        if (name.isEmpty()) {
            return CompletableFuture.completedFuture(
                    ToolExecutionResult.fail("name parameter is required", "MISSING_PARAM"));
        }
        Optional<SkillDefinition> def = skillService.get(name, cwd);
        if (def.isEmpty()) {
            List<String> available = skillService.list(cwd).stream().map(s -> s.name()).limit(50).toList();
            return CompletableFuture.completedFuture(ToolExecutionResult.fail(
                    "skill not found: " + name + ". Available: " + String.join(", ", available),
                    "SKILL_NOT_FOUND"));
        }
        SkillDefinition skill = def.get();
        String body = "<skill name=\"" + skill.name() + "\" source=\"" + skill.source()
                + "\" path=\"" + skill.path().orElse("") + "\">\n"
                + skill.content()
                + "\n</skill>\nFollow the skill instructions above for the remainder of this task.";
        List<ContentBlock> content = List.of(new TextBlock(body));
        return CompletableFuture.completedFuture(ToolExecutionResult.ok(content));
    }
    /**
     * 判断当前状态是否满足条件。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param args 启动参数。
     * @return 布尔结果：满足条件为 true，否则 false。
     */


    @Override
    public boolean isConcurrencySafe(Object args) { return true; }
}
