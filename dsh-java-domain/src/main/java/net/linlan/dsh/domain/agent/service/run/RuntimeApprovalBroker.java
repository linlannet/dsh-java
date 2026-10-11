package net.linlan.dsh.domain.agent.service.run;

import net.linlan.dsh.domain.agent.contract.outbound.RuntimeApprovalGateway;
import net.linlan.dsh.domain.tool.service.RuntimeApprovalGate.ApprovalRequest;
import net.linlan.dsh.domain.tool.service.RuntimeApprovalGate.ApprovalVerdict;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 运行期工具审批 Broker。
 * <p>
 * 对应原版 TS 的 gate.kind === 'ask' 实时挂起语义：agent 执行到需审批的工具时，
 * {@link #requestApproval} 注册一条待决审批并<b>阻塞等待</b>用户裁决；
 * 前端轮询 {@link #listPending}、通过 REST 提交裁决（{@link #resolve}），
 * 阻塞中的 agent 线程随即被唤醒继续执行。
 * <p>
 * 对齐 TS 的 allow-once / allowed-session / deny / cancel 四分支。
 *
 *
 */
public class RuntimeApprovalBroker implements RuntimeApprovalGateway {

    private static final Logger log = LoggerFactory.getLogger(RuntimeApprovalBroker.class);

    /** 默认审批等待超时：10 分钟（审批可以慢慢等，区别于普通工具执行超时） */
    public static final long DEFAULT_TIMEOUT_MS = 600_000;

    private final ConcurrentMap<String, PendingApproval> pending = new ConcurrentHashMap<>();
    private final long timeoutMs;

    /**
     * 运行time Approval Broker。
     */
    public RuntimeApprovalBroker() {
        this(DEFAULT_TIMEOUT_MS);
    }

    /**
     * 运行time Approval Broker。
     */
    public RuntimeApprovalBroker(long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    /**
     * 挂起一次工具调用，阻塞等待人工裁决。
     * 由 {@code MatrixRuntimeApprovalGate} 的 askHandler 调用。
     */
    public ApprovalVerdict requestApproval(ApprovalRequest request) {
        String approvalId = UUID.randomUUID().toString();
        CompletableFuture<ApprovalVerdict> future = new CompletableFuture<>();
        pending.put(approvalId, new PendingApproval(approvalId, request, future, Instant.now()));
        log.info("[approval] pending runtime approval {} for tool={} session={}", approvalId, request.toolName(), request.sessionId());
        try {
            ApprovalVerdict verdict = future.get(timeoutMs, TimeUnit.MILLISECONDS);
            log.info("[approval] resolved {} -> {}", approvalId, verdict);
            return verdict;
        } catch (TimeoutException e) {
            log.warn("[approval] {} timed out after {}ms, denying", approvalId, timeoutMs);
            return ApprovalVerdict.DENY;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ApprovalVerdict.CANCEL;
        } catch (Exception e) {
            log.warn("[approval] {} failed: {}", approvalId, e.getMessage());
            return ApprovalVerdict.DENY;
        } finally {
            pending.remove(approvalId);
        }
    }

    /**
     * 提交裁决（REST 端点调用）。
     *
     * @return true 表示裁决被接受；false 表示审批不存在或已裁决
     */
    public boolean resolve(String approvalId, ApprovalVerdict verdict) {
        PendingApproval pa = pending.get(approvalId);
        if (pa == null) return false;
        return pa.future.complete(verdict);
    }

    /** 列出当前全部待决审批（供前端轮询）。 */
    public List<PendingApprovalView> listPending() {
        return pending.values().stream()
                .map(pa -> new PendingApprovalView(
                        pa.id,
                        pa.request.sessionId(),
                        pa.request.toolName(),
                        pa.request.displayCommand(),
                        pa.request.arguments(),
                        pa.createdAt.toString()))
                .toList();
    }

    /** 待决数（测试与监控用）。 */
    public int pendingCount() {
        return pending.size();
    }

    /**
     * PendingApproval 是基础设施适配器，负责外部资源与领域端口对接。
     */
    private record PendingApproval(String id, ApprovalRequest request,
                                   CompletableFuture<ApprovalVerdict> future, Instant createdAt) {}

}
