package net.linlan.dsh.domain.runtime.meter.model.valobj;

import net.linlan.dsh.domain.llm.model.valobj.TokenUsage;

public record TokenUsageSummaryVO(
        String sessionId,
        long modelCalls,
        TokenUsage total
) {
}
