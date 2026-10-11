package net.linlan.dsh.domain.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import net.linlan.dsh.domain.task.submission.model.valobj.TaskSubmissionVO;
import net.linlan.dsh.domain.task.submission.service.TaskSubmissionPolicyService;
import net.linlan.dsh.domain.task.submission.service.filter.TaskSubmissionFilter;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TaskSubmissionPolicyServiceTest {

    @Test
    void appliesFiltersInInjectedOrder() {
        List<String> invoked = new ArrayList<>();
        TaskSubmissionFilter first = submission -> invoked.add("first");
        TaskSubmissionFilter second = submission -> invoked.add("second");
        TaskSubmissionPolicyService service = new TaskSubmissionPolicyService(List.of(first, second));
        var submission = new TaskSubmissionVO("workspace", "prompt", "headless", null, null);

        service.validate(submission);

        assertEquals(List.of("first", "second"), invoked);
    }

    @Test
    void stopsValidationWhenFilterFails() {
        List<String> invoked = new ArrayList<>();
        TaskSubmissionFilter failing = submission -> {
            invoked.add("failing");
            throw new IllegalArgumentException("invalid prompt");
        };
        TaskSubmissionFilter successful = submission -> invoked.add("successful");
        TaskSubmissionPolicyService service = new TaskSubmissionPolicyService(List.of(failing, successful));

        assertThrows(IllegalArgumentException.class,
                () -> service.validate(new TaskSubmissionVO("workspace", "prompt", "headless", null, null)));
        assertEquals(List.of("failing"), invoked);
    }
}
