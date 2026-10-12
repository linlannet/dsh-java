package net.linlan.dsh.domain.hooks.codex;

import net.linlan.dsh.domain.hooks.model.valobj.HookOutput;

import java.util.Optional;

/**
 * CodexHookBridge 实现 Codex hook 协议收发，解析 Codex 格式 JSON 并转换为内部 HookOutput。
 */
public final class CodexHookBridge {

    public HookOutput bridge(String hookInput) {
        if (hookInput == null || hookInput.isBlank()) {
            return emptyOutput();
        }
        String exitCodeRaw = extractStringField(hookInput, "exit_code");
        String stderr = extractStringField(hookInput, "stderr");
        String stdout = extractStringField(hookInput, "stdout");
        String continueRaw = extractStringField(hookInput, "continue");
        String stopReason = extractStringField(hookInput, "stopReason");
        String decisionRaw = extractStringField(hookInput, "decision");
        String reason = extractStringField(hookInput, "reason");
        String additionalContext = extractStringField(hookInput, "additionalContext");
        String systemMessage = extractStringField(hookInput, "systemMessage");

        Integer exitCode = parseInteger(exitCodeRaw);
        Optional<Boolean> continueFlag = parseBoolean(continueRaw);
        Optional<HookOutput.HookDecision> decision = parseDecision(decisionRaw);

        return new HookOutput(
                exitCode,
                stderr,
                stdout,
                continueFlag,
                stopReason != null ? Optional.of(stopReason) : Optional.empty(),
                decision,
                reason != null ? Optional.of(reason) : Optional.empty(),
                additionalContext != null ? Optional.of(additionalContext) : Optional.empty(),
                systemMessage != null ? Optional.of(systemMessage) : Optional.empty()
        );
    }

    public String toCodexJson(HookOutput output) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"exit_code\":").append(output.exitCode() != null ? output.exitCode() : "null");
        sb.append(",\"stderr\":").append(jsonString(output.stderr()));
        sb.append(",\"stdout\":").append(jsonString(output.stdout()));
        sb.append(",\"continue\":").append(output.continueFlag().isPresent() ? output.continueFlag().get() : "null");
        sb.append(",\"stopReason\":").append(output.stopReason().isPresent() ? jsonString(output.stopReason().get()) : "null");
        sb.append(",\"decision\":").append(output.decision().isPresent() ? jsonString(output.decision().get().name()) : "null");
        sb.append(",\"reason\":").append(output.reason().isPresent() ? jsonString(output.reason().get()) : "null");
        sb.append(",\"additionalContext\":").append(output.additionalContext().isPresent() ? jsonString(output.additionalContext().get()) : "null");
        sb.append(",\"systemMessage\":").append(output.systemMessage().isPresent() ? jsonString(output.systemMessage().get()) : "null");
        sb.append("}");
        return sb.toString();
    }

    private static HookOutput emptyOutput() {
        return new HookOutput(0, "", "", Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    private static String jsonString(String s) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        sb.append("\"");
        return sb.toString();
    }

    private static String extractStringField(String json, String field) {
        String key = "\"" + field + "\"";
        int idx = json.indexOf(key);
        if (idx < 0) return null;
        int colon = json.indexOf(':', idx + key.length());
        if (colon < 0) return null;
        int i = colon + 1;
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) i++;
        if (i >= json.length()) return null;
        char c = json.charAt(i);
        if (c == '"') {
            int end = i + 1;
            StringBuilder sb = new StringBuilder();
            while (end < json.length()) {
                char ec = json.charAt(end);
                if (ec == '\\' && end + 1 < json.length()) {
                    char next = json.charAt(end + 1);
                    switch (next) {
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        default -> sb.append(next);
                    }
                    end += 2;
                } else if (ec == '"') {
                    break;
                } else {
                    sb.append(ec);
                    end++;
                }
            }
            return sb.toString();
        } else {
            int end = i;
            while (end < json.length() && json.charAt(end) != ',' && json.charAt(end) != '}' && json.charAt(end) != ']') {
                end++;
            }
            String raw = json.substring(i, end).trim();
            return raw.equals("null") ? null : raw;
        }
    }

    private static Integer parseInteger(String raw) {
        if (raw == null || raw.isEmpty() || raw.equals("null")) return null;
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Optional<Boolean> parseBoolean(String raw) {
        if (raw == null || raw.isEmpty() || raw.equals("null")) return Optional.empty();
        if (raw.equals("true")) return Optional.of(true);
        if (raw.equals("false")) return Optional.of(false);
        return Optional.empty();
    }

    private static Optional<HookOutput.HookDecision> parseDecision(String raw) {
        if (raw == null || raw.isEmpty() || raw.equals("null")) return Optional.empty();
        try {
            return Optional.of(HookOutput.HookDecision.valueOf(raw.toUpperCase()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
