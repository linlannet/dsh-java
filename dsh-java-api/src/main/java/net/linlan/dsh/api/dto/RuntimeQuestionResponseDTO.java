package net.linlan.dsh.api.dto;

import java.util.List;

/**
 * 运行期问答响应 DTO。
 * <p>携带待处理问题列表，每个问题包含选项和是否多选标记。
 */
public record RuntimeQuestionResponseDTO(
        String questionId,
        String agentId,
        List<RuntimeQuestionItemDTO> questions,
        String createdAt
) {

    public record RuntimeQuestionOptionDTO(String label, String description) {}

    public record RuntimeQuestionItemDTO(
            String id,
            String question,
            String header,
            List<RuntimeQuestionOptionDTO> options,
            boolean multiSelect
    ) {}
}
