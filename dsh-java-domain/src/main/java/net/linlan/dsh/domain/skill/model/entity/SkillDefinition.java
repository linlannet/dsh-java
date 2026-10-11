package net.linlan.dsh.domain.skill.model.entity;

import net.linlan.dsh.domain.skill.model.valobj.SkillInvocationPolicy;
import net.linlan.dsh.domain.skill.model.valobj.SkillSource;
import java.util.Optional;

/**
 * SkillDefinition 表示SkillDefinition。
 */
public record SkillDefinition(
        String name,
        String description,
        Optional<String> whenToUse,
        SkillInvocationPolicy invocation,
        SkillSource source,
        String provider,
        String content,
        Optional<String> path
) {}
