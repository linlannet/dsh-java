package net.linlan.dsh.domain.todo.model.valobj;

import java.util.List;

/**
 * 待办写入结果。
 * <p>
 * 执行流程：
 * 1. 领域层保存完整待办列表；
 * 2. 统计状态分布；
 * 3. 返回给工具层输出。
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
public record TodoWriteResultVO(
        List<TodoItem> todos,
        int pending,
        int inProgress,
        int completed
) {
    public TodoWriteResultVO {
        todos = todos == null ? List.of() : List.copyOf(todos);
    }
}
