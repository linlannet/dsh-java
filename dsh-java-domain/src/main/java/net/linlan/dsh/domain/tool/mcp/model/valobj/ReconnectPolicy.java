package net.linlan.dsh.domain.tool.mcp.model.valobj;

/**
 * ReconnectPolicy 定义 MCP 客户端断连后的重连退避策略。
 *
 * @param initialBackoff    初始退避毫秒数
 * @param maxBackoff        最大退避毫秒数
 * @param maxAttempts       最大重连尝试次数
 * @param backoffMultiplier 退避倍率
 */
public record ReconnectPolicy(
        long initialBackoff,
        long maxBackoff,
        int maxAttempts,
        double backoffMultiplier
) {

    public ReconnectPolicy {
        if (initialBackoff <= 0) throw new IllegalArgumentException("initialBackoff must be positive");
        if (maxBackoff < initialBackoff) throw new IllegalArgumentException("maxBackoff must be >= initialBackoff");
        if (maxAttempts <= 0) throw new IllegalArgumentException("maxAttempts must be positive");
        if (backoffMultiplier <= 1.0) throw new IllegalArgumentException("backoffMultiplier must be > 1.0");
    }

    public static ReconnectPolicy default_() {
        return new ReconnectPolicy(100L, 30_000L, 5, 2.0);
    }

    /**
     * 计算第 attempt 次重连的退避毫秒数（attempt 从 0 开始）。
     *
     * @param attempt 重连尝试序号（0-based）
     * @return 退避毫秒数
     */
    public long nextBackoff(int attempt) {
        if (attempt < 0) attempt = 0;
        double backoff = initialBackoff * Math.pow(backoffMultiplier, attempt);
        return (long) Math.min(backoff, maxBackoff);
    }
}
