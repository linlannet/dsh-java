package net.linlan.dsh.cases.session.node;

import net.linlan.dsh.cases.orchestration.AbstractStrategyRouter;
import net.linlan.dsh.cases.orchestration.NoContext;
import net.linlan.dsh.cases.orchestration.StrategyHandler;
import net.linlan.dsh.api.dto.ConversationMessageResponseDTO;
import net.linlan.dsh.cases.support.CaseGuard;
import net.linlan.dsh.domain.session.IConversationQueryService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 会话消息查询节点。
 * <p>流程：校验 sessionId 非空 → 调用会话查询领域服务 → 转换为消息 DTO 列表。</p>
 * <p>数据示例：传入 {@code sessionId=BLUE-8090}；
 * 返回 {@code [{role: "USER", content: "记住我是蓝色", occurredAt: "2026-09-07T10:00:00Z"}]}。</p>
 */
@Service
public class ConversationMessagesNode
        extends AbstractStrategyRouter<String, NoContext, List<ConversationMessageResponseDTO>> {

    private static final Logger log = LoggerFactory.getLogger(ConversationMessagesNode.class);

    private final IConversationQueryService conversationQueryService;

    public ConversationMessagesNode(IConversationQueryService conversationQueryService) {
        this.conversationQueryService = conversationQueryService;
    }

    /**
     * 执行当前节点核心逻辑：校验输入、调用领域服务并返回输出。
     */
    @Override
    protected List<ConversationMessageResponseDTO> doApply(String sessionId, NoContext context) {
        CaseGuard.requireSessionId(sessionId);
        var result = conversationQueryService.listMessages(sessionId).stream()
                .map(message -> new ConversationMessageResponseDTO(
                        message.role(), message.content(), message.blocks(), message.occurredAt()
                ))
                .toList();
        log.debug("[Session] 会话消息查询完成，sessionId={} count={}", sessionId, result.size());
        return result;
    }

    /**
     * 返回下一执行节点；单节点链路返回默认终止处理。
     */
    @Override
    public StrategyHandler<String, NoContext, List<ConversationMessageResponseDTO>> getNext(
            String sessionId, NoContext context) {
        return defaultStrategyHandler;
    }
}
