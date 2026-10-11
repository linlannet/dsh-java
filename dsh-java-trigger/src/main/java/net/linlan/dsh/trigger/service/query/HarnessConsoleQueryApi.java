package net.linlan.dsh.trigger.service.query;

import net.linlan.dsh.api.dto.ConversationMessageResponseDTO;
import net.linlan.dsh.api.dto.ConversationSessionSummaryResponseDTO;
import net.linlan.dsh.api.IHarnessConsoleQueryApi;
import net.linlan.dsh.api.response.Response;
import net.linlan.dsh.cases.session.IConversationQueryCase;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 会话查询适配器。
 * <p>
 * 执行流程：
 * 1. 接收 Trigger 层的会话查询请求；
 * 2. 调用领域查询服务读取会话摘要和消息；
 * 3. 映射成前端可直接使用的响应对象。
 *
 * <p>调用流程：Controller/DTO → 应用门面 → 用例；响应对象由用例结果转换。</p>
 * <p>示例：DTO 只做协议隔离，不承载领域规则。</p>
 *
 */
@Service
public class HarnessConsoleQueryApi implements IHarnessConsoleQueryApi {

    private final IConversationQueryCase conversationQueryCase;

    /**
     * 处理Harness Console Query API。
     * 流程：转换请求 → 调用用例或领域服务 → 转换响应。
     * @param conversationQueryService conversation Query Service参数。
     * @return 方法执行结果。
     */
    public HarnessConsoleQueryApi(IConversationQueryCase conversationQueryCase) {
        this.conversationQueryCase = conversationQueryCase;
    }

    /**
     * 查询最近的会话摘要。
     * <p>
     * 执行流程：
     * 1. 调用领域查询服务读取会话摘要；
     * 2. 转换为接口响应对象；
     * 3. 返回给前端侧边栏。
     */
    public Response<List<ConversationSessionSummaryResponseDTO>> listRecentSessions(int limit, int offset) {
        return execute(() -> conversationQueryCase.listRecentSessions(limit, offset));
    }

    /**
     * 查询单个会话消息列表。
     * <p>
     * 执行流程：
     * 1. 根据 sessionId 读取领域会话消息；
     * 2. 将消息转为响应对象；
     * 3. 返回给前端会话详情页。
     */
    public Response<List<ConversationMessageResponseDTO>> listMessages(String sessionId) {
        return execute(() -> conversationQueryCase.listMessages(sessionId));
    }

    private <T> Response<T> execute(java.util.function.Supplier<T> action) {
        try {
            return Response.success(action.get());
        } catch (IllegalArgumentException exception) {
            return Response.invalidArgument(exception.getMessage());
        } catch (RuntimeException exception) {
            return Response.internalError(exception.getMessage());
        }
    }
}
