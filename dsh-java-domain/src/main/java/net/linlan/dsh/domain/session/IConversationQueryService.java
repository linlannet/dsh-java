package net.linlan.dsh.domain.session;

import net.linlan.dsh.domain.session.conversation.model.valobj.ConversationMessageVO;
import net.linlan.dsh.domain.session.conversation.model.valobj.ConversationSessionDetailVO;
import net.linlan.dsh.domain.session.conversation.model.valobj.ConversationSessionSummaryVO;
import java.util.List;

/**
 * 会话查询服务接口。
 * <p>
 * 执行流程：
 * 1. 上层请求会话摘要或消息；
 * 2. 领域层从持久化数据装配读模型；
 * 3. 返回 UI 可直接渲染的数据结构。
 *
 * <p>调用流程：事件写入 SessionEventLog → 事件溯源重建会话 → 查询或恢复。</p>
 * <p>示例：追加用户消息、模型消息和工具结果事件后，可按序号回放完整对话。</p>
 * 
 */
public interface IConversationQueryService {

    List<ConversationSessionSummaryVO> listRecentSessions(int limit, int offset);

    List<ConversationMessageVO> listMessages(String sessionId);

    /**
     * 分页查询会话摘要。
     * <p>
     * 执行流程：
     * 1. 读取工作区和分页参数；
     * 2. 查询对应会话并截取范围；
     * 3. 返回分页读模型。
     */
    List<ConversationSessionSummaryVO> querySessions(String workspaceId, int offset, int limit);

    /**
     * 查询会话详情。
     * <p>
     * 执行流程：
     * 1. 读取 sessionId；
     * 2. 装配会话主数据和消息列表；
     * 3. 返回完整详情。
     */
    ConversationSessionDetailVO querySessionDetail(String sessionId);
}
