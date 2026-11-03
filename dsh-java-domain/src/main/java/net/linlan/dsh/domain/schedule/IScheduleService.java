package net.linlan.dsh.domain.schedule;

import net.linlan.dsh.domain.schedule.model.entity.ScheduleRecord;
import net.linlan.dsh.domain.schedule.model.valobj.ScheduleCreateRequest;
import net.linlan.dsh.domain.schedule.model.valobj.ScheduleEditRequest;
import net.linlan.dsh.domain.schedule.model.valobj.SchedulePage;
import net.linlan.dsh.domain.schedule.model.valobj.ScheduleView;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 计划任务领域契约。
 *
 * <p>宿主范围：所有读写按任务 id 寻址，不再限制在调用方会话内；{@code sourceSessionId} 仅作创建者记录。</p>
 */
public interface IScheduleService {

    /**
     * 创建计划任务。
     *
     * @param request 创建请求
     * @return 任务记录
     */
    ScheduleRecord create(ScheduleCreateRequest request);

    /**
     * 按 id 查看任务。
     *
     * @param id 任务 ID
     * @return 视图，不存在时为空
     */
    Optional<ScheduleView> view(String id);

    /**
     * 分页列出宿主任务。
     *
     * @param active true 仅活动任务；false 或 null 表示全部
     * @param limit 分页大小，<=0 使用默认值
     * @param offset 偏移量
     * @return 分页结果
     */
    SchedulePage list(Boolean active, int limit, int offset);

    /**
     * 编辑或删除任务。
     *
     * @param request 编辑请求
     * @return 变更后的任务记录（删除时返回已结束快照）
     */
    ScheduleRecord edit(ScheduleEditRequest request);

    /**
     * 驱动到期任务，返回本次触发的记录。
     *
     * @param now 当前时刻
     * @return 触发的任务记录
     */
    List<ScheduleRecord> drive(Instant now);
}
