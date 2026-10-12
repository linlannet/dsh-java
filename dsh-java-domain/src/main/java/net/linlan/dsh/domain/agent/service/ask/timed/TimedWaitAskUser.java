package net.linlan.dsh.domain.agent.service.ask.timed;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * TimedWaitAskUser 提供带超时的提问能力，超时后以默认值继续，并支持接受迟到回复。
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
public class TimedWaitAskUser {

    private final ConcurrentMap<String, PendingAsk> pending = new ConcurrentHashMap<>();

    public AskResult ask(String question, Duration timeout, String defaultValue) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("question required");
        }
        if (timeout == null || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be non-negative");
        }
        String questionId = UUID.randomUUID().toString();
        CompletableFuture<String> future = new CompletableFuture<>();
        pending.put(questionId, new PendingAsk(questionId, question, future, System.currentTimeMillis()));

        try {
            String reply = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            return new AskResult(questionId, reply, false);
        } catch (TimeoutException e) {
            return new AskResult(questionId, defaultValue, true);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new AskResult(questionId, defaultValue, true);
        } catch (java.util.concurrent.ExecutionException e) {
            return new AskResult(questionId, defaultValue, true);
        } finally {
            pending.remove(questionId);
        }
    }

    public boolean acceptLateReply(String questionId, String reply) {
        if (questionId == null || questionId.isBlank()) {
            return false;
        }
        PendingAsk entry = pending.get(questionId);
        if (entry == null) {
            return false;
        }
        return entry.future().complete(reply);
    }

    public boolean hasPending(String questionId) {
        return questionId != null && pending.containsKey(questionId);
    }

    public java.util.List<String> listPendingQuestionIds() {
        return List.copyOf(pending.keySet());
    }

    public record AskResult(
            String questionId,
            String reply,
            boolean timedOut
    ) {}

    public record PendingAsk(
            String questionId,
            String question,
            CompletableFuture<String> future,
            long createdAt
    ) {}
}
