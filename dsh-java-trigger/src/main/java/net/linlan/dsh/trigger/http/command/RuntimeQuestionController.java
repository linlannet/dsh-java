package net.linlan.dsh.trigger.http.command;

import net.linlan.dsh.api.IRuntimeQuestionApi;
import net.linlan.dsh.api.dto.RuntimeQuestionResponseDTO;
import net.linlan.dsh.api.dto.SubmitUserAnswerRequestDTO;
import net.linlan.dsh.api.dto.SubmitUserAnswerResponseDTO;
import net.linlan.dsh.api.response.Response;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value={"/api/harness/questions/runtime"})
public class RuntimeQuestionController {
    private final IRuntimeQuestionApi runtimeQuestionApi;

    public RuntimeQuestionController(IRuntimeQuestionApi runtimeQuestionApi) {
        this.runtimeQuestionApi = runtimeQuestionApi;
    }

    @GetMapping(value={"/pending"})
    public Response<List<RuntimeQuestionResponseDTO>> listPending() {
        return this.runtimeQuestionApi.listPending();
    }

    @PostMapping(value={"/{questionId}/answer"})
    public Response<SubmitUserAnswerResponseDTO> submitAnswer(@PathVariable(value="questionId") String questionId, @RequestBody SubmitUserAnswerRequestDTO request) {
        return this.runtimeQuestionApi.submitAnswer(questionId, request);
    }
}

