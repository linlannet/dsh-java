package net.linlan.dsh.infrastructure.adapter.llm;

import net.linlan.dsh.domain.llm.adapter.LlmAdapter;
import net.linlan.dsh.domain.llm.adapter.ResolvedRetryPolicy;
import net.linlan.dsh.domain.llm.model.valobj.FinishReason;
import net.linlan.dsh.domain.llm.model.valobj.GenerateOptions;
import net.linlan.dsh.domain.llm.model.valobj.LlmFailure;
import net.linlan.dsh.domain.llm.model.valobj.LlmErrorCodes;
import net.linlan.dsh.domain.llm.model.valobj.StreamChunk;
import net.linlan.dsh.domain.llm.adapter.port.ILlmRuntimePort;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * 内存版大模型运行时端口实现。
 * <p>
 * 执行流程：
 * 1. 维护 provider 到适配器的映射；
 * 2. 根据 provider 路由到具体模型适配器；
 * 3. 将异常统一包装成结束 chunk 返回。
 *
 * <p>调用流程：模型请求 → HTTP 客户端 → 解析 JSON/SSE → 归一化为领域事件。</p>
 * <p>示例：上游返回 token delta 时，适配器转换为 LLM 增量事件。</p>
 *
 */
public class InMemoryLlmRuntimePort implements ILlmRuntimePort {

    private static final Logger log = LoggerFactory.getLogger(InMemoryLlmRuntimePort.class);

    private final Map<String, LlmAdapter> adapters = new ConcurrentHashMap<>();

    /**
     * 兜底适配器工厂：为未显式注册的 provider 按请求协议动态创建适配器。
     * 为 null 时未注册 provider 直接路由失败（保持旧行为）。
     */
    private volatile java.util.function.Function<String, LlmAdapter> fallbackFactory;

    /**
     * 处理fallback Factory。
     * 流程：设置兜底工厂 → 未注册 provider 请求时按协议动态创建适配器。
     *
     * @param fallbackFactory 兜底适配器工厂
     */
    public void setFallbackFactory(java.util.function.Function<String, LlmAdapter> fallbackFactory) {
        this.fallbackFactory = fallbackFactory;
    }

    @Override
    public boolean hasAdapter(String provider) {
        return adapters.containsKey(provider);
    }

    /**
     * 解析 provider 对应适配器；未注册时尝试经兜底工厂按协议动态创建并缓存。
     */
    private LlmAdapter resolveAdapter(String provider) {
        LlmAdapter adapter = adapters.get(provider);
        if (adapter == null && fallbackFactory != null) {
            adapter = adapters.computeIfAbsent(provider, p -> {
                log.info("Provider '{}' 未显式注册适配器，使用协议路由适配器兜底", p);
                return fallbackFactory.apply(p);
            });
        }
        return adapter;
    }

    /**
     * 注册模型 Provider 对应的 LLM 适配器。
     * <p>流程：接收 provider 编码和适配器实例 → 写入内存路由表 → 记录注册成功日志。
     * <p>示例：注册 provider=deepseek 后，请求 provider=deepseek 会路由到 DeepSeek 适配器。
     *
     * @param provider 模型提供方编码
     * @param adapter LLM 适配器实现
     */
    @Override
    public void registerAdapter(String provider, LlmAdapter adapter) {
        adapters.put(provider, adapter);
        log.info("Registered LLM adapter for provider: {}", provider);
    }

    /**
     * 取消模型 Provider 的适配器注册。
     * <p>流程：接收 provider 编码 → 从内存路由表移除适配器 → 记录注销成功日志。
     * <p>示例：注销 provider=deepseek 后，后续该 provider 的请求会收到路由失败结果。
     *
     * @param provider 模型提供方编码
     */
    @Override
    public void unregisterAdapter(String provider) {
        adapters.remove(provider);
        log.info("Unregistered LLM adapter for provider: {}", provider);
    }

    /**
     * 列出当前已注册的模型 Provider。
     * <p>流程：遍历内存路由表 → 让适配器生成展示信息 → 汇总为 Provider 列表。
     * <p>示例：注册 deepseek、openai 后返回这两个 provider 的展示信息。
     *
     * @return 已注册 Provider 展示信息列表
     */
    @Override
    public List<LlmAdapter.LlmProviderInfo> listProviders() {
        List<LlmAdapter.LlmProviderInfo> result = new ArrayList<>();
        for (var entry : adapters.entrySet()) {
            result.add(entry.getValue().providerInfo(entry.getKey()));
        }
        return result;
    }

    /**
     * 列出指定 Provider 的可用模型。
     * <p>流程：按 provider 查找适配器 → 委托适配器读取模型目录 → 未找到时返回空列表。
     * <p>示例：provider=deepseek 且适配器存在时，返回 DeepSeek 适配器暴露的模型列表。
     *
     * @param provider 模型提供方编码
     * @return 可用模型列表；Provider 未注册时为空列表
     */
    @Override
    public List<LlmAdapter.LlmModelInfo> listModels(String provider) {
        LlmAdapter adapter = adapters.get(provider);
        return adapter != null ? adapter.listModels(provider) : List.of();
    }

    /**
     * 解析指定 Provider 下的模型元数据。
     * <p>流程：按 provider 查找适配器 → 委托适配器定位模型 → 返回上下文、默认 token 等信息。
     * <p>数据示例：provider=deepseek、model=deepseek-chat 会返回该模型的上下文窗口和默认输出上限。
     *
     * @param provider 模型提供方编码
     * @param model 模型编码
     * @return 已解析的模型信息
     * @throws IllegalArgumentException Provider 未注册适配器时抛出
     */
    @Override
    public LlmAdapter.LlmResolvedModelInfo resolveModel(String provider, String model) {
        LlmAdapter adapter = adapters.get(provider);
        if (adapter == null) {
            String message = "No adapter registered for provider '" + provider
                    + "'. Available providers: " + adapters.keySet()
                    + ". Check providerCode in model settings.";
            log.error("模型路由失败：provider='{}' 未注册适配器，可用 providers={}。"
                            + "请检查 Web 控制台模型设置中的 providerCode。", provider, adapters.keySet());
            throw new IllegalArgumentException(message);
        }
        return adapter.resolveModel(provider, model);
    }

    /**
     * 通过注册的适配器发起流式模型调用。
     * <p>流程：按 provider 查找适配器 → 读取重试策略 → 订阅适配器输出 → 统一包装增量、异常和结束事件。
     * <p>数据示例：provider=deepseek 请求流式补全时，适配器返回的 token delta 会被逐个发布给调用方；
     * Provider 不存在时直接发布一条 Error 结束事件。
     *
     * @param options 模型生成参数
     * @return 可订阅的模型流
     */
    @Override
    public Flow.Publisher<StreamChunk> stream(GenerateOptions options) {
        LlmAdapter adapter = resolveAdapter(options.provider());
        if (adapter == null) {
            log.error("模型路由失败：provider='{}' 未注册适配器，可用 providers={}。"
                            + "请检查 Web 控制台模型设置中的 providerCode。",
                    options.provider(), adapters.keySet());
            SubmissionPublisher<StreamChunk> publisher = new SubmissionPublisher<>();
            publisher.submit(new StreamChunk.Finish(new FinishReason.Error(new LlmFailure(
                    "No adapter registered for provider '" + options.provider()
                            + "'. Available providers: " + adapters.keySet()
                            + ". Check providerCode in model settings.",
                    LlmErrorCodes.UNKNOWN
            ))));
            publisher.close();
            return publisher;
        }

        ResolvedRetryPolicy policy = adapter.retryPolicy(options.provider());
        if (policy != null) {
            return retryingStream(adapter, options, policy);
        }

        // 包装适配器的流式输出，统一异常表现
        SubmissionPublisher<StreamChunk> wrapper = new SubmissionPublisher<>();
        adapter.stream(options).subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(StreamChunk item) {
                wrapper.submit(item);
            }

            @Override
            public void onError(Throwable throwable) {
                LlmFailure failure = new LlmFailure(
                        throwable.getMessage() != null ? throwable.getMessage() : "LLM adapter failed",
                        LlmErrorCodes.UNKNOWN
                );
                log.error("LLM stream error for provider={}", options.provider(), throwable);
                wrapper.submit(new StreamChunk.Finish(new FinishReason.Error(failure)));
                wrapper.close();
            }

            @Override
            public void onComplete() {
                wrapper.close();
            }
        });

        return wrapper;
    }
    /**
     * 对流式模型调用执行重试。
     * <p>流程：发起流式请求 → chunk 实时桥接转发给下游 → 仅在未吐出任何内容时发生可重试错误才按退避策略重建流。</p>
     * <p>注意：一旦已向下方转发过任何内容 chunk，失败后不再重试（重试会产生重复内容），直接透传错误终态。</p>
     */
    private Flow.Publisher<StreamChunk> retryingStream(
            LlmAdapter adapter,
            GenerateOptions options,
            ResolvedRetryPolicy policy
    ) {
        SubmissionPublisher<StreamChunk> wrapper = new SubmissionPublisher<>();
        Thread retryThread = new Thread(() -> runWithRetry(adapter, options, policy, wrapper));
        retryThread.setName("llm-retry-" + options.provider());
        retryThread.setDaemon(true);
        retryThread.start();
        return wrapper;
    }

    /**
     * 运行With Retry。
     */
    private void runWithRetry(
            LlmAdapter adapter,
            GenerateOptions options,
            ResolvedRetryPolicy policy,
            SubmissionPublisher<StreamChunk> wrapper
    ) {
        int attemptCount = policy instanceof ResolvedRetryPolicy.Normal normal
                ? normal.maxRetries()
                : Integer.MAX_VALUE;
        AtomicInteger attempt = new AtomicInteger();

        while (true) {
            attempt.incrementAndGet();
            // 桥接式实时转发：内容 chunk 立即透传给下游，只截留终态 Finish 块用于重试判定
            AttemptOutcome outcome = forwardAttempt(adapter.stream(options), wrapper);

            // 仅在「尚未吐出任何内容」时发生可重试错误，才安全重建流
            if (outcome.completed() && !outcome.forwardedAny() && isRetryable(outcome.failure(), policy)) {
                if (attempt.get() > attemptCount) {
                    LlmFailure exhausted = withRetryCount(outcome.failure(), attempt.get() - 1);
                    wrapper.submit(new StreamChunk.Finish(new FinishReason.Error(exhausted)));
                    wrapper.close();
                    return;
                }
                Integer maxAttempts = policy instanceof ResolvedRetryPolicy.Normal normal
                        ? normal.maxRetries()
                        : null;
                wrapper.submit(new StreamChunk.Retry(
                        attempt.get(),
                        maxAttempts,
                        outcome.failure() == null ? "model request failed" : outcome.failure().message()
                ));
                if (!sleep(retryDelayMs(policy, attempt.get(), outcome.failure()))) {
                    wrapper.close();
                    return;
                }
                continue;
            }

            // 终态：补发被截留的 Finish 块（若有），然后关闭流
            if (outcome.finishChunk() != null) {
                wrapper.submit(outcome.finishChunk());
            }
            if (outcome.error() != null) {
                log.error("LLM stream error for provider={}", options.provider(), outcome.error());
                wrapper.closeExceptionally(outcome.error());
            } else {
                wrapper.close();
            }
            return;
        }
    }

    /**
     * 执行单次流式尝试，桥接式实时转发。
     * <p>流程：订阅适配器流 → 内容 chunk 立即转发给 wrapper → 终态 Finish 块截留 → 等待流结束。</p>
     * <p>截留 Finish 的原因：若本次尝试在未吐出任何内容时以可重试错误结束，
     * 该错误结束块不应发给下游，由重试逻辑决定后续动作。</p>
     *
     * @param publisher 适配器流
     * @param wrapper 对外发布的包装流
     * @return 单次尝试结果
     */
    private AttemptOutcome forwardAttempt(
            Flow.Publisher<StreamChunk> publisher,
            SubmissionPublisher<StreamChunk> wrapper
    ) {
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicBoolean forwardedAny = new java.util.concurrent.atomic.AtomicBoolean();
        java.util.concurrent.atomic.AtomicReference<Throwable> error = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<StreamChunk.Finish> finishChunk = new java.util.concurrent.atomic.AtomicReference<>();

        publisher.subscribe(new Flow.Subscriber<>() {
            @Override public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override public void onNext(StreamChunk item) {
                if (item instanceof StreamChunk.Finish terminal) {
                    // 截留终态块，待重试判定后再决定是否补发
                    finishChunk.set(terminal);
                    return;
                }
                forwardedAny.set(true);
                wrapper.submit(item);
            }
            /**
             * on Error。
             * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
             * @param throwable throwable参数。
             */


            @Override public void onError(Throwable throwable) {
                error.set(throwable);
                done.countDown();
            }
            /**
             * on Complete。
             * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
             * @return 方法执行结果。
             */


            @Override public void onComplete() {
                done.countDown();
            }
        });

        try {
            done.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            error.set(exception);
        }
        StreamChunk.Finish terminal = finishChunk.get();
        FinishReason terminalFinish = terminal == null ? null : terminal.reason();
        LlmFailure failure = terminalFinish instanceof FinishReason.Error errorFinish
                ? errorFinish.failure()
                : null;
        return new AttemptOutcome(forwardedAny.get(), error.get(), failure, terminalFinish != null, terminal);
    }

    /**
     * 判断Retryable是否成立。
     */
    private boolean isRetryable(LlmFailure failure, ResolvedRetryPolicy policy) {
        if (failure == null) return false;
        if (policy instanceof ResolvedRetryPolicy.Always) return true;
        if (policy instanceof ResolvedRetryPolicy.Normal normal) {
            return normal.retryableCodes().contains(failure.code());
        }
        return false;
    }

    /**
     * 处理sleep。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param delayMs delay Ms参数。
     * @return 方法执行结果。
     */
    private boolean sleep(long delayMs) {
        try {
            Thread.sleep(delayMs);
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * 计算下一次重试延迟。
     * <p>流程：读取基础延迟 → 应用指数退避和上限 → 返回等待毫秒数。</p>
     */
    private long retryDelayMs(
            ResolvedRetryPolicy policy,
            int failedAttempt,
            LlmFailure failure
    ) {
        long localDelay = (long) Math.min(
                policy.maxDelayMs(),
                (double) policy.initialDelayMs() * Math.pow(2, failedAttempt - 1)
        );
        Long providerDelay = failure == null ? null : failure.providerRetryAfterMs();
        if (providerDelay != null && providerDelay > 0 && providerDelay <= policy.maxDelayMs()) {
            return providerDelay;
        }
        double jitter = 1 + (ThreadLocalRandom.current().nextDouble(2) - 1) * policy.jitterRatio();
        return Math.max(0, Math.round(localDelay * jitter));
    }

    /**
     * 处理with Retry 数量。
     * 流程：适配外部协议或资源 → 调用底层能力 → 返回结果。
     * @param failure failure参数。
     * @param retryCount retry 数量参数。
     * @return 方法执行结果。
     */
    private LlmFailure withRetryCount(LlmFailure failure, int retryCount) {
        String suffix = "（已重试 " + retryCount + " 次）";
        return new LlmFailure(
                failure.message() + suffix,
                failure.code(),
                failure.status(),
                failure.providerRetryAfterMs(),
                failure.requestId()
        );
    }

    /**
     * 单次流式尝试的结果。
     *
     * @param forwardedAny 是否已向下游转发过内容 chunk（已转发则不可安全重试）
     * @param error 流式过程中的异常
     * @param failure 终态 Finish 中携带的失败信息
     * @param completed 是否收到了终态 Finish 块
     * @param finishChunk 被截留的终态 Finish 块（不重试时需补发给下游）
     */
    private record AttemptOutcome(
            boolean forwardedAny,
            Throwable error,
            LlmFailure failure,
            boolean completed,
            StreamChunk.Finish finishChunk
    ) {}
}
