package net.linlan.dsh.cases.orchestration;

/**
 * Creates the executable pipeline for a multi-step case.
 *
 * @param <I> request type
 * @param <C> orchestration context type
 * @param <O> result type
 */
public interface CasePipelineFactory<I, C, O> {

    CasePipeline<I, C, O> pipeline();
}
