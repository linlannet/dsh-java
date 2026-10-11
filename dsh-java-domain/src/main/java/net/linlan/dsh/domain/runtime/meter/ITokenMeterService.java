package net.linlan.dsh.domain.runtime.meter;

import net.linlan.dsh.domain.runtime.meter.model.valobj.TokenUsageSummaryVO;

public interface ITokenMeterService {

    TokenUsageSummaryVO summarize(String sessionId);
}
