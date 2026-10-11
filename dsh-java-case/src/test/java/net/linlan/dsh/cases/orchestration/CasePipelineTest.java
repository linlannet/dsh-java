package net.linlan.dsh.cases.orchestration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CasePipelineTest {

    @Test
    void executesRootWithFreshContext() throws Exception {
        AtomicInteger contextCount = new AtomicInteger();
        CasePipeline<String, StringBuilder, String> pipeline = CasePipeline.of(
                "append",
                (request, context) -> {
                    context.append(request);
                    return context.toString();
                },
                () -> {
                    contextCount.incrementAndGet();
                    return new StringBuilder();
                }
        );

        assertEquals("done", pipeline.execute("done"));
        assertEquals(1, contextCount.get());
    }

    @Test
    void rethrowsRuntimeWithoutWrapping() {
        IllegalStateException failure = new IllegalStateException("runtime");
        CasePipeline<String, Object, String> pipeline = CasePipeline.of(
                "runtime",
                (request, context) -> {
                    throw failure;
                },
                Object::new
        );

        assertSame(failure, assertThrows(IllegalStateException.class, () -> pipeline.execute("request")));
    }

    @Test
    void wrapsCheckedFailureWithOperationName() {
        CasePipeline<String, Object, String> pipeline = CasePipeline.of(
                "checked",
                (request, context) -> {
                    throw new Exception("failure");
                },
                Object::new
        );

        CaseExecutionException exception = assertThrows(
                CaseExecutionException.class,
                () -> pipeline.execute("request")
        );
        assertEquals("checked failed", exception.getMessage());
    }
}
