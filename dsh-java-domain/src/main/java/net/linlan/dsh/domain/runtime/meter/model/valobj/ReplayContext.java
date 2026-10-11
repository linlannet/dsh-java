package net.linlan.dsh.domain.runtime.meter.model.valobj;

/**
 * ReplayContext 标识当前 Token 计量是实时计算还是重放恢复。
 *
 * @param isReplay     是否为重放模式
 * @param upToStepIndex 重放覆盖到的最大事件步索引（仅 isReplay=true 时有意义）
 */
public record ReplayContext(
        boolean isReplay,
        long upToStepIndex
) {

    public static ReplayContext live() {
        return new ReplayContext(false, Long.MAX_VALUE);
    }

    public static ReplayContext replay(long upToStepIndex) {
        return new ReplayContext(true, upToStepIndex);
    }
}
