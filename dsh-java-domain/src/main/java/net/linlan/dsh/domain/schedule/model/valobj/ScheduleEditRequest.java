package net.linlan.dsh.domain.schedule.model.valobj;

import java.time.Instant;

/**
 * 编辑或删除计划任务的请求（对应参考源 {@code schedule_edit} 的 {@code action}）。
 *
 * @param id 任务 ID
 * @param action {@code update} 或 {@code delete}
 * @param patch 变更补丁，{@code delete} 时可为空
 * @param expectedUpdatedAt 期望的版本时间戳，用于乐观并发；null 表示不校验
 */
public record ScheduleEditRequest(
        String id,
        String action,
        SchedulePatch patch,
        Instant expectedUpdatedAt
) {

    /** 更新动作。 */
    public static final String ACTION_UPDATE = "update";

    /** 删除动作。 */
    public static final String ACTION_DELETE = "delete";

    public ScheduleEditRequest {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id is required");
        }
        if (action == null || action.isBlank()) {
            throw new IllegalArgumentException("action is required: update | delete");
        }
        String normalized = action.trim().toLowerCase();
        if (!ACTION_UPDATE.equals(normalized) && !ACTION_DELETE.equals(normalized)) {
            throw new IllegalArgumentException("invalid action: " + action + ", expected update | delete");
        }
        if (ACTION_UPDATE.equals(normalized) && (patch == null || patch.isEmpty())) {
            throw new IllegalArgumentException("patch is required for action=update");
        }
    }

    /** 是否为删除动作。 */
    public boolean deleteAction() {
        return ACTION_DELETE.equals(action.trim().toLowerCase());
    }
}
