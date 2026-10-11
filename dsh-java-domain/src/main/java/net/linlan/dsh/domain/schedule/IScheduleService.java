package net.linlan.dsh.domain.schedule;

import net.linlan.dsh.domain.schedule.model.entity.ScheduleRecord;
import net.linlan.dsh.domain.schedule.model.valobj.ScheduleView;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * IScheduleService 领域契约。
 * <p>职责：承载领域规则、状态或边界，供上层编排与基础设施适配。</p>
 */
public interface IScheduleService {

    ScheduleRecord createAfter(String sessionId, String prompt, Duration delay);

    ScheduleRecord createAt(String sessionId, String prompt, Instant target);

    ScheduleRecord createEvery(String sessionId, String prompt, Duration interval);

    List<ScheduleView> list(String sessionId);

    boolean delete(String sessionId, String id);

    /**
     * 驱动到期任务。
     * <p>流程：校验入参 → 应用领域规则 → 返回结果。</p>
     * @param sessionId 会话ID。
     * @param now now。
     * @return 执行结果。
     */
    List<ScheduleRecord> drive(String sessionId, Instant now);
}
