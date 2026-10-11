package net.linlan.dsh.domain.agent.service;

import net.linlan.dsh.domain.agent.model.entity.AgentOptions;
import net.linlan.dsh.domain.agent.model.valobj.AgentCancelCause;
import net.linlan.dsh.domain.agent.model.valobj.AgentStatus;
import net.linlan.dsh.domain.agent.model.valobj.InboxTarget;
import net.linlan.dsh.domain.model.entity.Message;
import net.linlan.dsh.domain.session.event.model.entity.SessionLog;
import java.util.concurrent.CompletableFuture;

/**
 * Public live-agent handle. Mirrors TS {@code Agent} interface.
 *
 * <p>调用流程：模型输出 → 工具调用 → 结果回填 → 下一步模型调用；每一步写入会话事件。</p>
 * <p>示例：模型请求执行 fs_read 后，工具结果作为观察值进入下一轮模型输入。</p>
 *
 */
public interface Agent {

    /**
     * 处理ID。
     * @return 领域处理结果。
     */
    String id();

    /**
     * 处理智能体选项。
     * @return 领域处理结果。
     */
    AgentOptions options();

    /**
     * 处理会话。
     * @return 领域处理结果。
     */
    SessionLog session();

    /**
     * 处理inbox。
     * @return 领域处理结果。
     */
    Inbox inbox();

    /**
     * 处理状态。
     * @return 领域处理结果。
     */
    AgentStatus status();

    /**
     * 处理cancel。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param cause cause。
     * @return 领域处理结果。
     */
    void cancel(AgentCancelCause cause, boolean keepInbox);

    /**
     * 处理whenIdle。
     * @return 领域处理结果。
     */
    CompletableFuture<Void> whenIdle();

    /**
     * 在空闲阶段执行一次维护任务并保持任务完成状态。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param task task。
     * @return 领域处理结果。
     */
    <T> CompletableFuture<T> runMaintenance(java.util.function.Function<
            java.util.concurrent.atomic.AtomicBoolean, CompletableFuture<T>> task);

    /**
     * 将输入路由到收件箱边界，并决定是否唤醒驱动。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param message 消息。
     * @param wakeup wakeup。
     * @return 领域处理结果。
     */
    void send(Message message, InboxTarget target, boolean wakeup);

    /**
     * 追加下一轮跟进消息并唤醒驱动。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param message 消息。
     * @return 领域处理结果。
     */
    default void followup(Message message) {
        send(message, InboxTarget.NEXT_TURN, true);
    }

    /**
     * 注入最近一步的纠偏消息。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param message 消息。
     * @return 领域处理结果。
     */
    default void steer(Message message) {
        send(message, InboxTarget.NEXT_STEP, true);
    }

    /**
     * 注入模型可见上下文但不唤醒驱动。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param message 消息。
     * @return 领域处理结果。
     */
    default void inject(Message message) {
        send(message, InboxTarget.NEXT_STEP, false);
    }
}
