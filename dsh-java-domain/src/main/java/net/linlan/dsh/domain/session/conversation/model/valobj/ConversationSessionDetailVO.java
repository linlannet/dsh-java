package net.linlan.dsh.domain.session.conversation.model.valobj;

import java.time.Instant;
import java.util.List;

/**
 * 会话详情值对象。
 * <p>
 * 执行流程：
 * 1. 查询服务读取会话主数据；
 * 2. 合并消息列表和运行时信息；
 * 3. 返回给前端详情页直接使用。
 *
 * @param sessionId 会话编号
 * @param workspaceId 工作区编号
 * @param status 会话状态
 * @param modelCode 模型编号
 * @param profileCode 执行画像编码
 * @param createdAt 创建时间
 * @param lastActivityAt 最近活动时间
 * @param messages 会话消息列表
 *
 *
 */
public record ConversationSessionDetailVO(
        String sessionId,
        String workspaceId,
        String status,
        String modelCode,
        String profileCode,
        Instant createdAt,
        Instant lastActivityAt,
        List<ConversationMessageVO> messages
) {
}
