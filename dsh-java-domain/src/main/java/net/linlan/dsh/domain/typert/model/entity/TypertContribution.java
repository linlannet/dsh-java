package net.linlan.dsh.domain.typert.model.entity;

import net.linlan.dsh.domain.typert.model.valobj.TypertFace;
import java.util.List;
import java.util.Map;

/**
 * TypertContribution 表示TypertContribution。
 */
public record TypertContribution(
        String packageName,
        TypertFace face,
        List<Map.Entry<String, Map<String, Object>>> schemas,
        List<InvocationDescriptor> invocations
) {}
