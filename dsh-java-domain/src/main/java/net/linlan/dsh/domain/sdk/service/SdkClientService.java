package net.linlan.dsh.domain.sdk.service;

import net.linlan.dsh.domain.sdk.ISdkClientService;
import net.linlan.dsh.domain.sdk.adapter.port.ISdkTransportPort;
import net.linlan.dsh.domain.sdk.model.valobj.*;

import java.util.Map;
import java.util.function.Consumer;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Service;

/**
 * SDK client service: sends requests over the transport port and routes
 * notifications to registered handlers.
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
@Service
@Conditional(SdkStandaloneCondition.class)
public class SdkClientService implements ISdkClientService {

    private final ISdkTransportPort transport;
    private volatile Consumer<SessionStatus> statusHandler;
    private volatile Consumer<Object> eventHandler;

    /**
     * 处理Sdk Client Service。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param transport transport参数。
     * @return 方法执行结果。
     */
    public SdkClientService(ISdkTransportPort transport) {
        this.transport = transport;
        this.transport.onNotification(frame -> {
            switch (frame.method()) {
                case "session.status" -> {
                    if (statusHandler != null) statusHandler.accept(SessionStatus.IDLE);
                }
                case "session.event" -> {
                    if (eventHandler != null) eventHandler.accept(frame.params());
                }
            }
        });
    }

    @Override
    /**
     * initialize。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param params params参数。
     * @return 方法执行结果。
     */

    @SuppressWarnings("unchecked")
    public InitializeResult initialize(InitializeParams params) {
        Map<String, Object> result = (Map<String, Object>) transport.request("initialize", params);
        return new InitializeResult(
                (String) result.get("serverName"),
                (String) result.get("serverVersion")
        );
    }

    @Override
    /**
     * session Prompt。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param params params参数。
     * @return 方法执行结果。
     */

    @SuppressWarnings("unchecked")
    public SessionPromptResult sessionPrompt(SessionPromptParams params) {
        Map<String, Object> result = (Map<String, Object>) transport.request("session/prompt", params);
        return new SessionPromptResult((String) result.get("messageId"));
    }
    /**
     * shutdown。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     */


    @Override
    public void shutdown() {
        transport.request("shutdown", null);
        transport.close();
    }
    /**
     * on Session Status。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param handler handler参数。
     */


    @Override
    public void onSessionStatus(Consumer<SessionStatus> handler) {
        this.statusHandler = handler;
    }
    /**
     * on Session Event。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param handler handler参数。
     */


    @Override
    public void onSessionEvent(Consumer<Object> handler) {
        this.eventHandler = handler;
    }
}
