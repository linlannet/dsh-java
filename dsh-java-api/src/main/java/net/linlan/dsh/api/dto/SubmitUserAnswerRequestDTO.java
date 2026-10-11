package net.linlan.dsh.api.dto;

import java.util.List;

/**
 * 提交用户答案请求 DTO。
 * <p>每个答案项对应一个问题，包含选中的选项标签列表和自定义文本。
 */
public record SubmitUserAnswerRequestDTO(List<AnswerItemDTO> answers) {

    public record AnswerItemDTO(String id, List<String> selected, String custom) {}
}
