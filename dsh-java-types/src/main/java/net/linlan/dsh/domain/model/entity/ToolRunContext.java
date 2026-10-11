package net.linlan.dsh.domain.model.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 注册表接受 {@link ToolExecution} 后，传递给工具实现的运行时上下文。
 * 对应 TS 侧 {@code ToolRunContext}。
 *
 * <p>Provides:
 * <ul>
 *   <li>{@link #deferContext(Message)} — 延迟一条上下文消息，直到工具最终结果
 *       回到 Agent 循环后再输出</li>
 *   <li>{@link #concludeTurn()} — 将成功结果标记为当前回合的终止结果</li>
 * </ul>
 *
 *
 */
public final class ToolRunContext {

    private final ToolExecution execution;
    private final List<Message> deferredContexts = new ArrayList<>();
    private final List<String> artifacts = new ArrayList<>();
    private final AtomicBoolean turnConcluded = new AtomicBoolean(false);

    /**
     * 创建工具执行上下文。
     * @param execution 工具执行定义。
     */
    public ToolRunContext(ToolExecution execution) {
        this.execution = execution;
    }

    /** 本次执行的身份标识。 */
    public ToolExecution execution() {
        return execution;
    }

    /** 延迟一条上下文消息，供 Agent 循环在工具结果后输出。 */
    public void deferContext(Message context) {
        if (context != null) {
            deferredContexts.add(context);
        }
    }

    /** 记录一个工件路径，供 Agent 循环在工具结果后收集。 */
    public void recordArtifact(String path) {
        if (path != null && !path.isBlank() && !artifacts.contains(path)) {
            artifacts.add(path);
        }
    }

    /** 取走并清空已记录的工件路径。 */
    public List<String> drainArtifacts() {
        synchronized (artifacts) {
            if (artifacts.isEmpty()) {
                return List.of();
            }
            List<String> copy = List.copyOf(artifacts);
            artifacts.clear();
            return copy;
        }
    }

    /** 将当前工具结果标记为本回合的终止结果。 */
    public void concludeTurn() {
        turnConcluded.set(true);
    }

    /** 是否已经调用过 concludeTurn()。 */
    public boolean isTurnConcluded() {
        return turnConcluded.get();
    }

    /** 清空延迟上下文消息，由调度器在处理结果后调用。 */
    public List<Message> drainDeferredContexts() {
        synchronized (deferredContexts) {
            return List.copyOf(deferredContexts);
        }
    }
}
