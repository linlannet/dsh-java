package net.linlan.dsh.cases.session;

import net.linlan.dsh.api.dto.ConversationMessageResponseDTO;
import net.linlan.dsh.api.dto.ConversationSessionSummaryResponseDTO;

import java.util.List;

/**
 * 会话消息与最近会话查询用例契约。
 * <p>流程：Trigger/前端 → 本接口 → SessionPipelineFactory → 会话查询领域服务。</p>
 * <p>数据示例：最近会话传入 {@code limit=20}；消息查询传入 {@code sessionId=BLUE-8090}，返回角色和内容时间线。</p>
 */
public interface IConversationQueryCase {
    List<ConversationSessionSummaryResponseDTO> listRecentSessions(int limit, int offset);
    List<ConversationMessageResponseDTO> listMessages(String sessionId);
}
