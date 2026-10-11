package net.linlan.dsh.domain.todo.model.valobj;

/**
 * 待办项。
 * <p>
 * 执行流程：
 * 1. 工具接收完整任务列表；
 * 2. 领域层校验内容和状态；
 * 3. 写入会话事件并返回最新快照。
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
public record TodoItem(
        String content,
        Status status
) {
    /** 待办状态。 */
    public enum Status {
        PENDING("pending"),
        IN_PROGRESS("in_progress"),
        COMPLETED("completed");

        private final String wire;

        Status(String wire) {
            this.wire = wire;
        }

        /**
         * 处理wire。
         * 流程：应用领域规则 → 更新领域对象 → 返回结果。
         * @return 方法执行结果。
         */
        public String wire() {
            return wire;
        }

        /**
         * 根据 Wire。
         * 流程：应用领域规则 → 更新领域对象 → 返回结果。
         * @param value 参数值。
         * @return 创建的领域对象。
         */
        public static Status fromWire(String value) {
            return switch (value) {
                case "pending" -> PENDING;
                case "in_progress" -> IN_PROGRESS;
                case "completed" -> COMPLETED;
                default -> throw new IllegalArgumentException("unknown todo status: " + value);
            };
        }
    }

    public TodoItem {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("todo content must be non-empty");
        }
        if (status == null) {
            throw new IllegalArgumentException("todo status must be non-null");
        }
    }
}
