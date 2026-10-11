package net.linlan.dsh.domain.sdk.adapter.port;

import java.util.function.Consumer;

/**
 * Port for newline-delimited JSON-RPC 2.0 transport over byte streams.
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
public interface ISdkTransportPort {

    /**
     * 处理请求。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param method method。
     * @return 领域处理结果。
     */
    Object request(String method, Object params);

    /**
     * 处理notify。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param method method。
     * @return 领域处理结果。
     */
    void notify(String method, Object params);

    /**
     * 处理onNotification。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param handler handler。
     * @return 领域处理结果。
     */
    void onNotification(Consumer<NotificationFrame> handler);

    /**
     * NotificationFrame 表示NotificationFrame。
     */
    void close();

    /**
     * NotificationFrame 表示NotificationFrame。
     */
    record NotificationFrame(String method, Object params) {}
}
