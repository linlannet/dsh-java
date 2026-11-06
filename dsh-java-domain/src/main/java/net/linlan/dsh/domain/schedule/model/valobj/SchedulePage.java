package net.linlan.dsh.domain.schedule.model.valobj;

import java.util.List;

/**
 * 计划任务分页结果（对应参考源 {@code { items, shown, total, limit, offset }}）。
 *
 * @param items 当前页条目
 * @param shown 当前页条目数
 * @param total 满足过滤条件的总数
 * @param limit 分页大小
 * @param offset 偏移量
 */
public record SchedulePage(List<ScheduleView> items, int shown, int total, int limit, int offset) {

    public SchedulePage {
        items = items == null ? List.of() : List.copyOf(items);
    }

    /**
     * 构造分页结果并自动填充 {@code shown}。
     *
     * @param items 条目
     * @param total 总数
     * @param limit 分页大小
     * @param offset 偏移量
     * @return 分页结果
     */
    public static SchedulePage of(List<ScheduleView> items, int total, int limit, int offset) {
        return new SchedulePage(items, items == null ? 0 : items.size(), total, limit, offset);
    }
}
