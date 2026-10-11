package net.linlan.dsh.domain.workflow.model.entity;

import net.linlan.dsh.domain.workflow.model.valobj.WorkflowMeta;
import net.linlan.dsh.domain.workflow.model.valobj.WorkflowResult;
import java.util.concurrent.CompletableFuture;

/**
 * 工作流运行句柄。
 * <p>
 * 执行流程：
 * 1. 工作流启动后创建运行句柄；
 * 2. 运行过程中可查询结果或发起取消；
 * 3. 运行结束后由句柄负责完成收敛和清理。
 *
 * <p>调用流程：定义工作流 → 启动 → 节点推进 → 事件/状态回写。</p>
 * <p>示例：工作流按节点顺序执行，每个节点可写回下一步上下文。</p>
 *
 */
public class WorkflowRun {

    private final String id;
    private final WorkflowMeta meta;
    private final CompletableFuture<WorkflowResult> result;
    private volatile boolean cancelled = false;

    /**
     * 处理工作流 Run。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param id ID参数。
     * @param meta meta参数。
     * @param result 结果对象。
     * @return 方法执行结果。
     */
    public WorkflowRun(String id, WorkflowMeta meta, CompletableFuture<WorkflowResult> result) {
        this.id = id;
        this.meta = meta;
        this.result = result;
    }

    /**
     * 处理ID。
     * @return 方法执行结果。
     */
    public String id() { return id; }
    /**
     * 处理meta。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @return 方法执行结果。
     */
    public WorkflowMeta meta() { return meta; }
    /**
     * 处理结果。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @return 方法执行结果。
     */
    public CompletableFuture<WorkflowResult> result() { return result; }

    /**
     * 取消工作流运行。
     * <p>
     * 执行流程：
     * 1. 标记运行已取消；
     * 2. 如果结果尚未完成则补一个取消结果；
     * 3. 交由上层等待结果收敛。
     */
    public void cancel(String reason) {
        cancelled = true;
        if (!result.isDone()) {
            result.complete(WorkflowResult.cancelled(0));
        }
    }

    /**
     * 释放工作流运行资源。
     * <p>
     * 执行流程：
     * 1. 先触发取消；
     * 2. 等待结果 future 收敛；
     * 3. 返回无返回值的完成信号。
     */
    public CompletableFuture<Void> dispose() {
        cancel(null);
        return result.thenRun(() -> {});
    }

    /**
     * 判断Cancelled是否成立。
     */
    public boolean isCancelled() { return cancelled; }
}
