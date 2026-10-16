package net.linlan.dsh.domain.guard.review.model.valobj;

import java.util.Map;

/**
 * ReviewPromptTemplate 可配置的审查 prompt 模板，支持变量占位符替换。
 */
public record ReviewPromptTemplate(String name, String template, String description) {

    public String render(Map<String, String> variables) {
        String result = template;
        if (variables != null) {
            for (var entry : variables.entrySet()) {
                result = result.replace("${" + entry.getKey() + "}", entry.getValue() == null ? "" : entry.getValue());
            }
        }
        return result;
    }

    public static ReviewPromptTemplate defaultReview() {
        return new ReviewPromptTemplate(
                "default-review",
                "You are a code review assistant. Review the following action:\nAction: ${action}\nContext: ${context}\nDetermine if this action is safe. Respond with ALLOW or DENY and a reason.",
                "Default review prompt template"
        );
    }

    public static ReviewPromptTemplate securityReview() {
        return new ReviewPromptTemplate(
                "security-review",
                "You are a security reviewer. Analyze the following action for security risks:\nAction: ${action}\nContext: ${context}\nCheck for: command injection, path traversal, privilege escalation. Respond with ALLOW or DENY and a reason.",
                "Security-focused review prompt template"
        );
    }
}
