package net.linlan.dsh.domain.agent.service.subagent;

import java.util.UUID;

/**
 * 子 agent 会话的标识约定。
 *
 * <p>职责：进程内派生（spawn / fork）的子会话统一以 {@value #SESSION_PREFIX} 前缀创建，
 * 供需要对子 agent 做能力限制的工具（如 Schedule）识别调用方身份。</p>
 * <p>示例：{@code SubagentSessions.isSubagent("child-3f2a...")} 返回 true。</p>
 */
public final class SubagentSessions {

    /** 子 agent 会话 ID 前缀。 */
    public static final String SESSION_PREFIX = "child-";

    private SubagentSessions() {
    }

    /**
     * 生成一个新的子会话 ID。
     *
     * @return 子会话 ID
     */
    public static String newSessionId() {
        return SESSION_PREFIX + UUID.randomUUID();
    }

    /**
     * 判断会话是否属于被委派的子 agent。
     *
     * @param sessionId 会话 ID
     * @return 子 agent 会话返回 true
     */
    public static boolean isSubagent(String sessionId) {
        return sessionId != null && sessionId.startsWith(SESSION_PREFIX);
    }
}
