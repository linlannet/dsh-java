package net.linlan.dsh.domain.workflow.service;

import net.linlan.dsh.domain.workflow.model.valobj.WorkflowResult;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class WorkflowStartPolicyTest {

    @Test
    void appliesAdhocNameAndCopiesArguments() {
        var request = WorkflowStartPolicy.adhoc(
                "return 1;",
                "  ",
                Map.of("key", "value"),
                "/tmp/workspace"
        );

        assertEquals("adhoc-workflow", request.meta().name());
        @SuppressWarnings("unchecked")
        Map<String, Object> args = (Map<String, Object>) request.args().orElseThrow();
        assertEquals("value", args.get("key"));
        assertEquals("/tmp/workspace", request.parentCwd());
    }

    @Test
    void mapsErrorsToErrorEvents() {
        var policy = WorkflowEventPolicy.completed(
                "run-1",
                WorkflowResult.completed("done", 0),
                new IllegalStateException("boom")
        );

        assertEquals(WorkflowEventPolicy.Kind.ERROR, policy.kind());
        assertEquals("boom", policy.error());
    }

    @Test
    void mapsCompletedResultsToDoneEvents() {
        var policy = WorkflowEventPolicy.completed(
                "run-1",
                WorkflowResult.completed("done", 0),
                null
        );

        assertEquals(WorkflowEventPolicy.Kind.DONE, policy.kind());
        assertEquals("COMPLETED", policy.status());
        assertEquals("done", policy.output());
    }
}
