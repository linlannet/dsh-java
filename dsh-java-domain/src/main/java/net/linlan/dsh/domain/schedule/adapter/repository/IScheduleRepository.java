package net.linlan.dsh.domain.schedule.adapter.repository;

import net.linlan.dsh.domain.schedule.model.entity.ScheduleRecord;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 计划任务仓储契约（宿主范围）。
 */
public interface IScheduleRepository {

    /**
     * 保存任务。
     *
     * @param record 任务记录
     */
    void save(ScheduleRecord record);

    /**
     * 按 id 删除。
     *
     * @param id 任务 ID
     */
    void deleteById(String id);

    /**
     * 按 id 查询。
     *
     * @param id 任务 ID
     * @return 任务记录
     */
    Optional<ScheduleRecord> findById(String id);

    /**
     * 列出全部任务。
     *
     * @return 任务记录
     */
    List<ScheduleRecord> findAll();

    /**
     * 分页查询。
     *
     * @param activeOnly 是否只取活动任务
     * @param limit 分页大小
     * @param offset 偏移量
     * @return 任务记录
     */
    List<ScheduleRecord> findPage(boolean activeOnly, int limit, int offset);

    /**
     * 统计数量。
     *
     * @param activeOnly 是否只统计活动任务
     * @return 数量
     */
    int count(boolean activeOnly);

    /**
     * 查询到期任务。
     *
     * @param now 当前时刻
     * @return 到期任务
     */
    List<ScheduleRecord> findDue(Instant now);
}
