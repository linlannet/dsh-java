package net.linlan.dsh.domain.schedule.model.valobj;

/**
 * 计划任务的投递目标。
 *
 * <p>对应参考源 {@code target: this_session | new_once | new}。目标决定任务触发后回执落到哪里：
 * 调用方会话、一次性新会话、或可复用的新会话。</p>
 */
public enum ScheduleTarget {
    /** 投递回调用方会话。 */
    THIS_SESSION,
    /** 触发时新建一次性会话。 */
    NEW_ONCE,
    /** 触发时新建可复用会话。 */
    NEW;

    /**
     * 解析模型工具传入的目标值。
     *
     * @param value 目标字符串
     * @return 目标枚举
     */
    public static ScheduleTarget fromWire(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("target is required: this_session | new_once | new");
        }
        String normalized = value.trim().toUpperCase();
        for (ScheduleTarget target : values()) {
            if (target.name().equals(normalized)) {
                return target;
            }
        }
        throw new IllegalArgumentException(
                "invalid target: " + value + ", expected this_session | new_once | new");
    }

    /**
     * 投递模式描述，用于视图展示。
     *
     * @return 模式描述
     */
    public String deliveryMode() {
        return switch (this) {
            case THIS_SESSION -> "session-local";
            case NEW_ONCE -> "new-session-once";
            case NEW -> "new-session";
        };
    }
}
