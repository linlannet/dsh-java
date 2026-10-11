package net.linlan.dsh.domain.session.conversation.model.valobj;

import java.time.Instant;
import java.util.List;

/**
 * 会话摘要值对象。
 * <p>
 * 执行流程：
 * 1. 由查询服务根据会话和消息装配；
 * 2. 作为侧边栏和管理列表的读模型；
 * 3. 供 API 层直接映射为响应对象。
 *
 * @param sessionId 会话编号
 * @param title 会话标题
 * @param preview 最近可见消息摘要
 * @param status 当前会话状态
 * @param createdAt 会话创建时间
 * @param workspaceId 工作区编号
 * @param modelCode 模型编号
 * @param profileCode 执行画像编码
 * @param lastActivityAt 最近活动时间
 * @param agentId 侧边栏 Agent 标识（Agent 会话从 agentPreset 还原，任务会话为 null）
 *
 *
 */
public record ConversationSessionSummaryVO(
        String sessionId,
        String title,
        String preview,
        String status,
        Instant createdAt,
        String workspaceId,
        String modelCode,
        String profileCode,
        Instant lastActivityAt,
        String agentId
) {
    /**
     * 仅用于侧边栏的简化构造方法。
     * <p>
     * 执行流程：
     * 1. 传入基础摘要字段；
     * 2. 其余读模型字段默认置空；
     * 3. 返回侧边栏可用的摘要对象。
     */
    public ConversationSessionSummaryVO(String sessionId, String title, String preview,
                                        String status, Instant createdAt) {
        this(sessionId, title, preview, status, createdAt, null, null, null, null, null);
    }

    /**
     * 兼容旧调用方的九参构造（不含 agentId）。
     */
    public ConversationSessionSummaryVO(String sessionId, String title, String preview,
                                        String status, Instant createdAt,
                                        String workspaceId, String modelCode, String profileCode,
                                        Instant lastActivityAt) {
        this(sessionId, title, preview, status, createdAt, workspaceId, modelCode, profileCode, lastActivityAt, null);
    }
}
