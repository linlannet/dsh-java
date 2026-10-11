package net.linlan.dsh.domain.agent.service.run;

import net.linlan.dsh.domain.agent.contract.outbound.UserQuestionGateway;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Simple in-memory implementation of {@link UserQuestionGateway}.
 * <p>
 * Stores pending questions in a map keyed by request id. When a user
 * response arrives (via REST endpoint or other channel), it completes
 * the future. This enables blocking-wait semantics for the agent loop
 * without requiring WebSocket or SSE infrastructure.
 *
 *
 */
public class InMemoryUserQuestionGateway implements UserQuestionGateway {

    private final ConcurrentMap<String, CompletableFuture<UserAnswer>> pending = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, PendingQuestion> pendingMeta = new ConcurrentHashMap<>();
    private final long defaultTimeoutMs;

    /**
     * 处理In Memory 用户 Questions Port。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @return 方法执行结果。
     */
    public InMemoryUserQuestionGateway() {
        this(300_000); // 5 min default timeout
    }

    /**
     * 处理In Memory 用户 Questions Port。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param defaultTimeoutMs default Timeout Ms参数。
     * @return 方法执行结果。
     */
    public InMemoryUserQuestionGateway(long defaultTimeoutMs) {
        this.defaultTimeoutMs = defaultTimeoutMs;
    }

    @Override
    public UserAnswer ask(UserQuestionRequest request) {
        String requestId = UUID.randomUUID().toString();
        CompletableFuture<UserAnswer> future = new CompletableFuture<>();
        pending.put(requestId, future);
        pendingMeta.put(requestId, new PendingQuestion(requestId, request.agentId(), request.questions(), System.currentTimeMillis()));
        try {
            return future.get(defaultTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            pending.remove(requestId);
            throw new RuntimeException("User question timed out", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            pending.remove(requestId);
            throw new RuntimeException("User question interrupted", e);
        } catch (Exception e) {
            pending.remove(requestId);
            throw new RuntimeException("User question failed", e);
        } finally {
            pending.remove(requestId);
            pendingMeta.remove(requestId);
        }
    }
    /**
     * 判断当前状态是否满足条件。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @return 布尔结果：满足条件为 true，否则 false。
     */


    @Override
    public boolean isAvailable() {
        return true;
    }

    /**
     * Submit a user's answer to a pending question.
     *
     * @param requestId the request id (returned from {@link #getPendingRequestId})
     * @param answer    the user's answer
     * @return true if the answer was accepted
     */
    public boolean submitAnswer(String requestId, UserAnswer answer) {
        CompletableFuture<UserAnswer> future = pending.get(requestId);
        if (future != null) {
            boolean accepted = future.complete(answer);
            if (accepted) {
                pendingMeta.remove(requestId);
            }
            return accepted;
        }
        return false;
    }

    /**
     * Get the current pending request ids (for UI polling).
     */
    public Map<String, CompletableFuture<UserAnswer>> getPending() {
        return Map.copyOf(pending);
    }

    /**
     * 获取当前待回答问题的元信息列表（按创建时间升序，供 UI 轮询）。
     */
    public List<PendingQuestion> getPendingQuestions() {
        return pendingMeta.values().stream()
                .sorted(Comparator.comparingLong(PendingQuestion::createdAt))
                .toList();
    }

    /**
     * 待回答问题的元信息。
     * @param requestId 请求 ID
     * @param agentId   智能体 ID
     * @param questions 问题列表
     * @param createdAt 创建时间戳（毫秒）
     */
    public record PendingQuestion(String requestId, String agentId, List<Question> questions, long createdAt) {
    }
}
