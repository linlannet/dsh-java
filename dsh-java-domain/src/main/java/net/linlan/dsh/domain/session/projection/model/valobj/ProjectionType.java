package net.linlan.dsh.domain.session.projection.model.valobj;

import java.util.Objects;

/**
 * ProjectionType 表示投影类型的值对象。
 * <p>预定义类型：{@link #SURFACE}（表面消息投影）、{@link #STATISTICS}（统计投影）、
 * {@link #TURN_OUTLINE}（轮次大纲投影）。
 */
public record ProjectionType(String name) {

    public ProjectionType {
        Objects.requireNonNull(name, "name");
        if (name.isEmpty()) {
            throw new IllegalArgumentException("projection type name must not be empty");
        }
    }

    /** 表面消息投影：提取用户可见的对话消息。 */
    public static final ProjectionType SURFACE = new ProjectionType("SURFACE");

    /** 统计投影：token 用量、轮次/步数计数等。 */
    public static final ProjectionType STATISTICS = new ProjectionType("STATISTICS");

    /** 轮次大纲投影：每轮的摘要、结束原因等。 */
    public static final ProjectionType TURN_OUTLINE = new ProjectionType("TURN_OUTLINE");
}
