package net.linlan.dsh.domain.terminal.model.valobj;

/**
 * TerminalBackendCleanupError 表示终端后端资源清理失败。
 *
 * <p>调用流程：关闭终端会话 → 终止进程失败 → 抛出本错误。</p>
 * <p>示例：进程在强制终止后仍存活时返回 {@code CLEANUP_FAILED}。</p>
 *
 * <p>聚合语义：Java 没有 {@code AggregateError}，这里用 {@link #cleanupFailure()} 作为 cause，
 * 并把同一次会话的启动失败放入 {@link #getSuppressed()}；{@link #isAggregate()} 表示是否同时携带启动失败。</p>
 */
public class TerminalBackendCleanupError extends TerminalError {

    /** 终端清理失败错误码。 */
    public static final String CLEANUP_FAILED = "CLEANUP_FAILED";

    private final Throwable startFailure;
    private final Throwable cleanupFailure;

    /**
     * 仅使用清理失败原因创建。
     *
     * @param message        可读消息
     * @param cleanupFailure 清理阶段原因
     */
    public TerminalBackendCleanupError(String message, Throwable cleanupFailure) {
        this(message, null, cleanupFailure);
    }

    /**
     * 使用启动失败与清理失败创建聚合错误。
     *
     * @param message        可读消息
     * @param startFailure   启动阶段原因，可为 null
     * @param cleanupFailure 清理阶段原因，可为 null
     */
    public TerminalBackendCleanupError(String message, Throwable startFailure, Throwable cleanupFailure) {
        super(CLEANUP_FAILED, message, cleanupFailure);
        this.startFailure = startFailure;
        this.cleanupFailure = cleanupFailure;
        if (startFailure != null && startFailure != cleanupFailure) {
            addSuppressed(startFailure);
        }
    }

    /**
     * 返回启动阶段失败原因。
     *
     * @return 启动失败原因，无则为 null
     */
    public Throwable startFailure() {
        return startFailure;
    }

    /**
     * 返回清理阶段失败原因。
     *
     * @return 清理失败原因，无则为 null
     */
    public Throwable cleanupFailure() {
        return cleanupFailure;
    }

    /**
     * 判断是否聚合了启动与清理两个阶段的失败。
     *
     * @return true 表示同时存在启动失败
     */
    public boolean isAggregate() {
        return startFailure != null;
    }
}
