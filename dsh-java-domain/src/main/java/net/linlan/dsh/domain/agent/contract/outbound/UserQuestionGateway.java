package net.linlan.dsh.domain.agent.contract.outbound;

import java.util.List;

/**
 * UserQuestionsPort 表示智能体与用户问答交互的领域端口。
 */
public interface UserQuestionGateway {

    /**
     * 向用户提出问题，并阻塞等待回答结果。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param request 请求。
     * @return 领域处理结果。
     */
    UserAnswer ask(UserQuestionRequest request);

    /**
     * Question 表示问题。
     */
    boolean isAvailable();

    // ── Types ───────────────────────────────────────────────────

    /**
     * Question 表示问题。
     */
    record Question(
            String id,
            String question,
            String header,
            List<QuestionOption> options,
            boolean multiSelect
    ) {
        public Question {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("question id required");
            if (question == null || question.isBlank()) throw new IllegalArgumentException("question text required");
        }
    }

    /**
     * QuestionOption 表示问题QuestionOption。
     */
    record QuestionOption(
            String label,
            String description
    ) {}

    /**
     * UserQuestionRequest 表示UserQuestionRequest问题请求。
     */
    record UserQuestionRequest(
            List<Question> questions,
            String agentId
    ) {}

    /**
     * Answer 表示Answer。
     */
    record Answer(
            String id,
            List<String> selected,
            String custom
    ) {}

    /**
     * UserAnswer 表示UserAnswer。
     */
    record UserAnswer(
            List<Answer> answers
    ) {}
}
