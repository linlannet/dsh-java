package net.linlan.dsh.trigger.service.command;

import net.linlan.dsh.api.IRuntimeQuestionApi;
import net.linlan.dsh.api.dto.RuntimeQuestionResponseDTO;
import net.linlan.dsh.api.dto.SubmitUserAnswerRequestDTO;
import net.linlan.dsh.api.dto.SubmitUserAnswerResponseDTO;
import net.linlan.dsh.api.response.Response;
import net.linlan.dsh.cases.question.IRuntimeQuestionCase;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;

@Service
public class RuntimeQuestionApi
implements IRuntimeQuestionApi {
    private final IRuntimeQuestionCase runtimeQuestionCase;

    public RuntimeQuestionApi(IRuntimeQuestionCase runtimeQuestionCase) {
        this.runtimeQuestionCase = runtimeQuestionCase;
    }

    public Response<List<RuntimeQuestionResponseDTO>> listPending() {
        return this.execute(() -> ((IRuntimeQuestionCase)this.runtimeQuestionCase).listPending());
    }

    public Response<SubmitUserAnswerResponseDTO> submitAnswer(String questionId, SubmitUserAnswerRequestDTO request) {
        return this.execute(() -> this.runtimeQuestionCase.submitAnswer(questionId, request));
    }

    private <T> Response<T> execute(Supplier<T> action) {
        try {
            return Response.success(action.get());
        }
        catch (IllegalArgumentException exception) {
            return Response.invalidArgument((String)exception.getMessage());
        }
        catch (RuntimeException exception) {
            return Response.internalError((String)exception.getMessage());
        }
    }
}

