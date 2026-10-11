package net.linlan.dsh.api;

import net.linlan.dsh.api.dto.RuntimeQuestionResponseDTO;
import net.linlan.dsh.api.dto.SubmitUserAnswerRequestDTO;
import net.linlan.dsh.api.dto.SubmitUserAnswerResponseDTO;
import net.linlan.dsh.api.response.Response;
import java.util.List;

/**
 * 运行期问答 API 接口。
 * <p>当 Agent 工具执行需要人工审批或选择时，通过此接口查询待处理问题并提交答案。
 */
public interface IRuntimeQuestionApi {

    Response<List<RuntimeQuestionResponseDTO>> listPending();

    Response<SubmitUserAnswerResponseDTO> submitAnswer(String questionId, SubmitUserAnswerRequestDTO request);
}
