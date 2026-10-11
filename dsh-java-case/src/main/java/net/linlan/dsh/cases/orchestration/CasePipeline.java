package net.linlan.dsh.cases.orchestration;

import java.util.function.Supplier;

/**
 * Executes a strategy tree behind one uniform case entry point.
 *
 * @param <I> request type
 * @param <C> orchestration context type
 * @param <O> result type
 */
public final class CasePipeline<I, C, O> {

    private final String operationName;
    private final StrategyHandler<I, C, O> rootHandler;
    private final Supplier<C> contextFactory;

    private CasePipeline(
            String operationName,
            StrategyHandler<I, C, O> rootHandler,
            Supplier<C> contextFactory
    ) {
        this.operationName = operationName;
        this.rootHandler = rootHandler;
        this.contextFactory = contextFactory;
    }

    /**
     * 创建 Pipeline，校验操作名、首节点和上下文工厂必填。
     */
    public static <I, C, O> CasePipeline<I, C, O> of(
            String operationName,
            StrategyHandler<I, C, O> rootHandler,
            Supplier<C> contextFactory
    ) {
        if (operationName == null || operationName.isBlank()) {
            throw new IllegalArgumentException("operationName must not be blank");
        }
        if (rootHandler == null) {
            throw new IllegalArgumentException("rootHandler must not be null");
        }
        if (contextFactory == null) {
            throw new IllegalArgumentException("contextFactory must not be null");
        }
        return new CasePipeline<>(operationName, rootHandler, contextFactory);
    }

    /**
     * 创建上下文、执行首节点链路，并在完成后清理可关闭上下文。
     */
    public O execute(I request) {
        C context = contextFactory.get();
        try {
            return rootHandler.apply(request, context);
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new CaseExecutionException(operationName + " failed", exception);
        } finally {
            if (context instanceof AutoCloseable closeable) {
                try {
                    closeable.close();
                } catch (Exception closeException) {
                    throw new CaseExecutionException(operationName + " context cleanup failed", closeException);
                }
            }
        }
    }
}
