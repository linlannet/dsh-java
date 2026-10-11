package net.linlan.dsh.domain.session.event.model.valobj;

/**
 * SurfaceEventType 表示SurfaceEventType事件类型。
 */
public enum SurfaceEventType {
    USER_MESSAGE,
    ASSISTANT_MESSAGE,
    TOOL_RESULT;

    /**
     * 判断Surface Event Type是否成立。
     */
    public static boolean isSurfaceEventType(SessionEventType type) {
        return switch (type) {
            case USER_MESSAGE, ASSISTANT_MESSAGE, TOOL_RESULT -> true;
            default -> false;
        };
    }

    /**
     * 转换为 会话 事件 类型。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @return 转换后的目标对象。
     */
    public SessionEventType toSessionEventType() {
        return switch (this) {
            case USER_MESSAGE -> SessionEventType.USER_MESSAGE;
            case ASSISTANT_MESSAGE -> SessionEventType.ASSISTANT_MESSAGE;
            case TOOL_RESULT -> SessionEventType.TOOL_RESULT;
        };
    }
}
