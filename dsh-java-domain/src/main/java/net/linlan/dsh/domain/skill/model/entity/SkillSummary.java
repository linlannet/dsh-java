package net.linlan.dsh.domain.skill.model.entity;

import net.linlan.dsh.domain.skill.model.valobj.SkillInvocationPolicy;
import net.linlan.dsh.domain.skill.model.valobj.SkillSource;
import java.util.Optional;

/**
 * SkillSummary 表示SkillSummary。
 */
public record SkillSummary(
        String name,
        String description,
        Optional<String> whenToUse,
        SkillInvocationPolicy invocation,
        SkillSource source,
        String provider,
        Optional<String> path
) {}
