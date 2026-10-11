package net.linlan.dsh.domain.llm.adapter;

import java.util.List;

/**
 * ResolvedRetryPolicy 表示ResolvedRetryPolicy。
 */
public sealed interface ResolvedRetryPolicy permits
        ResolvedRetryPolicy.Normal,
        ResolvedRetryPolicy.Always {

    int initialDelayMs();
    int maxDelayMs();
    double jitterRatio();

    /**
     * Normal 表示Normal。
     */
    record Normal(
            int initialDelayMs,
            int maxDelayMs,
            double jitterRatio,
            int maxRetries,
            List<String> retryableCodes
    ) implements ResolvedRetryPolicy {
        public Normal {
            retryableCodes = List.copyOf(retryableCodes);
        }
    }

    /**
     * Always 表示Alway列表。
     */
    record Always(
            int initialDelayMs,
            int maxDelayMs,
            double jitterRatio
    ) implements ResolvedRetryPolicy {}
}
