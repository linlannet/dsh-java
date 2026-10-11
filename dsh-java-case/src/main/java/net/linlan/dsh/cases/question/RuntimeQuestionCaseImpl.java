package net.linlan.dsh.cases.question;

import net.linlan.dsh.api.dto.RuntimeQuestionResponseDTO;
import net.linlan.dsh.api.dto.SubmitUserAnswerRequestDTO;
import net.linlan.dsh.api.dto.SubmitUserAnswerResponseDTO;
import net.linlan.dsh.cases.support.CaseGuard;
import net.linlan.dsh.domain.agent.contract.outbound.UserQuestionGateway;
import net.linlan.dsh.domain.agent.service.run.InMemoryUserQuestionGateway;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 运行期问答用例实现。
 * <p>从 InMemoryUserQuestionGateway 读取待处理问题并转换为 DTO；
 * 接收用户答案后委托 gateway.submitAnswer 完成人工裁决。
 */
@Service
public class RuntimeQuestionCaseImpl implements IRuntimeQuestionCase {

    private static final Logger log = LoggerFactory.getLogger(RuntimeQuestionCaseImpl.class);
    private final InMemoryUserQuestionGateway questionGateway;

    public RuntimeQuestionCaseImpl(UserQuestionGateway userQuestionGateway) {
        if (!(userQuestionGateway instanceof InMemoryUserQuestionGateway)) {
            throw new IllegalStateException(
                    "runtime question endpoints require " + InMemoryUserQuestionGateway.class.getSimpleName());
        }
        this.questionGateway = (InMemoryUserQuestionGateway) userQuestionGateway;
    }

    @Override
    public List<RuntimeQuestionResponseDTO> listPending() {
        return questionGateway.getPendingQuestions().stream()
                .map(item -> new RuntimeQuestionResponseDTO(
                        item.requestId(),
                        item.agentId(),
                        item.questions().stream().map(RuntimeQuestionCaseImpl::toQuestionItem).toList(),
                        Instant.ofEpochMilli(item.createdAt()).toString()))
                .toList();
    }

    @Override
    public SubmitUserAnswerResponseDTO submitAnswer(String questionId, SubmitUserAnswerRequestDTO request) {
        CaseGuard.requireText(questionId, "questionId must not be blank");
        if (request == null || request.answers() == null || request.answers().isEmpty()) {
            throw new IllegalArgumentException("answers must not be empty");
        }
        var answers = new ArrayList<UserQuestionGateway.Answer>();
        for (SubmitUserAnswerRequestDTO.AnswerItemDTO item : request.answers()) {
            CaseGuard.requireText(item.id(), "answer id must not be blank");
            List<String> selected = item.selected() == null ? List.of() : item.selected();
            String custom = item.custom() == null ? null : item.custom().trim();
            boolean hasCustom = custom != null && !custom.isEmpty();
            if (selected.isEmpty() && !hasCustom) {
                throw new IllegalArgumentException("answer " + item.id() + " requires selected options or custom text");
            }
            answers.add(new UserQuestionGateway.Answer(item.id(), List.copyOf(selected), hasCustom ? custom : null));
        }
        boolean submitted = questionGateway.submitAnswer(questionId, new UserQuestionGateway.UserAnswer(answers));
        if (submitted) {
            log.info("[Question] 用户回答已提交，questionId={} answers={}", questionId, answers.size());
            return new SubmitUserAnswerResponseDTO(true, null);
        }
        log.warn("[Question] 用户回答提交失败（问题不存在或已处理），questionId={}", questionId);
        return new SubmitUserAnswerResponseDTO(false, "question not found or already answered");
    }

    private static RuntimeQuestionResponseDTO.RuntimeQuestionItemDTO toQuestionItem(UserQuestionGateway.Question question) {
        return new RuntimeQuestionResponseDTO.RuntimeQuestionItemDTO(
                question.id(),
                question.question(),
                question.header(),
                question.options() == null ? List.of() : question.options().stream()
                        .map(option -> new RuntimeQuestionResponseDTO.RuntimeQuestionOptionDTO(
                                option.label(), option.description())).toList(),
                question.multiSelect());
    }
}
