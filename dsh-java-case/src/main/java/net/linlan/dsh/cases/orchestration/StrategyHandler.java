package net.linlan.dsh.cases.orchestration;

/**
 * Strategy handler: one node in a case orchestration tree.
 *
 * @param <I> request parameter type
 * @param <C> dynamic context type (mutable, flows through the chain)
 * @param <O> output type
 */
@FunctionalInterface
public interface StrategyHandler<I, C, O> {

    O apply(I requestParameter, C dynamicContext) throws Exception;
}
