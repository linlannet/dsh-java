package net.linlan.dsh.cases.question;

import net.linlan.dsh.api.dto.RuntimeQuestionResponseDTO;
import net.linlan.dsh.api.dto.SubmitUserAnswerRequestDTO;
import net.linlan.dsh.api.dto.SubmitUserAnswerResponseDTO;
import java.util.List;

/**
 * 运行期问答用例接口。
 * <p>查询待处理的人工审批/选择问题，并提交用户答案。
 */
public interface IRuntimeQuestionCase {

    List<RuntimeQuestionResponseDTO> listPending();

    SubmitUserAnswerResponseDTO submitAnswer(String questionId, SubmitUserAnswerRequestDTO request);
}
