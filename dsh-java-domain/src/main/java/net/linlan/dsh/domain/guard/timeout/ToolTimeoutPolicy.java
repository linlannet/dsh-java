package net.linlan.dsh.domain.guard.timeout;

import net.linlan.dsh.domain.guard.model.valobj.ToolTimeoutResult;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * ToolTimeoutPolicy 表示ToolTimeoutPolicy。
 */
public class ToolTimeoutPolicy {

    public static final String CODE = ToolTimeoutResult.CODE;

    /**
     * 处理arm。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param signal signal。
     * @param toolRunnable toolRunnable。
     * @return 领域处理结果。
     */
    public static Object arm(java.util.concurrent.atomic.AtomicReference<Object> signal,
                             long timeoutMs,
                             Callable<Object> toolRunnable) throws InterruptedException {
        CountDownLatch done = new CountDownLatch(1);
        Object[] holder = new Object[2]; // [0]=result, [1]=error

        Thread worker = new Thread(() -> {
            try {
                holder[0] = toolRunnable.call();
            } catch (Throwable t) {
                holder[1] = t;
            } finally {
                done.countDown();
            }
        });
        worker.setDaemon(true);
        worker.start();

        boolean finished = done.await(timeoutMs, TimeUnit.MILLISECONDS);
        if (!finished) {
            signal.set(new TimeoutFired());
            done.await(500, TimeUnit.MILLISECONDS);
            throw new ToolTimeoutException(timeoutMs);
        }

        if (holder[1] != null) throw new RuntimeException((Throwable) holder[1]);
        return holder[0];
    }

    /**
     * TimeoutFired 表示TimeoutFired。
     */
    public static final class TimeoutFired {}

    /**
     * ToolTimeoutException 表示ToolTimeoutException。
     */
    public static class ToolTimeoutException extends RuntimeException {
        private final long timeoutMs;
        /**
         * 转换为 ol Timeout Exception。
         * 流程：应用领域规则 → 更新领域对象 → 返回结果。
         * @param timeoutMs 超时时间，毫秒。
         * @return 方法执行结果。
         */
        public ToolTimeoutException(long timeoutMs) {
            super("tool call timed out after " + timeoutMs + "ms");
            this.timeoutMs = timeoutMs;
        }
        /**
         * 处理timeout Ms。
         * @return 方法执行结果。
         */
        public long timeoutMs() { return timeoutMs; }
    }
}
