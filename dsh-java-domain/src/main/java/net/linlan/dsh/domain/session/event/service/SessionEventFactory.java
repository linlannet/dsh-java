package net.linlan.dsh.domain.session.event.service;

import net.linlan.dsh.domain.model.entity.Message;
import net.linlan.dsh.domain.llm.model.valobj.TokenUsage;
import net.linlan.dsh.domain.model.entity.MessageSource;
import net.linlan.dsh.domain.model.valobj.TextBlock;
import net.linlan.dsh.domain.session.event.model.entity.SessionEvent;
import net.linlan.dsh.domain.session.event.model.entity.SessionLog;
import net.linlan.dsh.domain.session.event.model.valobj.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * SessionEventFactory 负责会话事件的创建与装配。
 */
public final class SessionEventFactory {

    /**
     * 处理会话 事件 Factory。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @return 方法执行结果。
     */
    private SessionEventFactory() {}

    /**
     * 获取当前事件时间戳。
     * @return UTC 时间戳。
     */
    private static Instant now() {
        return Instant.now();
    }

    // ── Turn / Step boundaries ───────────────────────────────────

    /**
     * 创建回合开始事件。
     * @return 回合开始事件。
     */
    public static SessionEvent.TurnStart turnStart() {
        return new SessionEvent.TurnStart(0, now());
    }

    /**
     * 使用指定时间创建回合开始事件。
     * @param time 事件时间。
     * @return 回合开始事件。
     */
    public static SessionEvent.TurnStart turnStart(Instant time) {
        return new SessionEvent.TurnStart(0, time);
    }

    /**
     * 创建回合结束事件。
     * @param 回合结束原因。
     * @return 回合结束事件。
     */
    public static SessionEvent.TurnEnd turnEnd(TurnEndReason reason) {
        return new SessionEvent.TurnEnd(0, now(), reason);
    }

    /**
     * 使用指定时间和原因创建回合结束事件。
     * @param time 事件时间。
     * @param 回合结束原因。
     * @return 回合结束事件。
     */
    public static SessionEvent.TurnEnd turnEnd(Instant time, TurnEndReason reason) {
        return new SessionEvent.TurnEnd(0, time, reason);
    }

    /**
     * 创建步开始事件。
     * @param turn 回合序号。
     * @param step 步序号。
     * @return 步开始事件。
     */
    public static SessionEvent.StepStart stepStart(long turn, long step) {
        return new SessionEvent.StepStart(0, now(), turn, step);
    }

    /**
     * 使用指定时间创建步开始事件。
     * @param time 事件时间。
     * @param turn 回合序号。
     * @param step 步序号。
     * @return 步开始事件。
     */
    public static SessionEvent.StepStart stepStart(Instant time, long turn, long step) {
        return new SessionEvent.StepStart(0, time, turn, step);
    }

    /**
     * 创建步结束事件。
     * @param turn 回合序号。
     * @param step 步序号。
     * @return 步结束事件。
     */
    public static SessionEvent.StepEnd stepEnd(long turn, long step) {
        return new SessionEvent.StepEnd(0, now(), turn, step);
    }

    /**
     * 使用指定时间创建步结束事件。
     * @param time 事件时间。
     * @param turn 回合序号。
     * @param step 步序号。
     * @return 步结束事件。
     */
    public static SessionEvent.StepEnd stepEnd(Instant time, long turn, long step) {
        return new SessionEvent.StepEnd(0, time, turn, step);
    }

    // ── Messages ─────────────────────────────────────────────────

    /**
     * 处理assistantChunk。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param turn 轮次。
     * @param chunk chunk。
     * @return 领域处理结果。
     */
    public static SessionEvent.AssistantChunk assistantChunk(long turn, long step, String chunk, String kind) {
        return new SessionEvent.AssistantChunk(0, now(),
                new AssistantChunkPayload(turn, step, chunk, kind));
    }

    /**
     * 处理userMessage消息。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param turn 轮次。
     * @param message 消息。
     * @return 领域处理结果。
     */
    public static SessionEvent.UserMessage userMessage(long turn, long step, Message message) {
        return new SessionEvent.UserMessage(0, now(),
                new UserMessagePayload(turn, step, message),
                new SurfaceIntent(SurfaceOp.append(), null));
    }

    /**
     * 处理userMessage消息。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param turn 轮次。
     * @param message 消息。
     * @return 领域处理结果。
     */
    public static SessionEvent.UserMessage userMessage(long turn, long step, Message message, SurfaceIntent intent) {
        return new SessionEvent.UserMessage(0, now(),
                new UserMessagePayload(turn, step, message),
                intent);
    }

    /**
     * 处理userMessage消息。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param content 内容。
     * @return 领域处理结果。
     */
    public static SessionEvent.UserMessage userMessage(String content) {
        Message msg = Message.createUser(
                List.of(new TextBlock(content)),
                new MessageSource.UserSource()
        );
        return userMessage(0, 0, msg);
    }

    /**
     * 处理injectedUserMessage消息。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param turn 轮次。
     * @param message 消息。
     * @return 领域处理结果。
     */
    public static SessionEvent.UserMessage injectedUserMessage(long turn, long step, Message message) {
        return new SessionEvent.UserMessage(0, now(),
                new UserMessagePayload(turn, step, message),
                new SurfaceIntent(SurfaceOp.append(), null));
    }

    /**
     * 处理assistantMessage消息。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param turn 轮次。
     * @param message 消息。
     * @return 领域处理结果。
     */
    public static SessionEvent.AssistantMessage assistantMessage(
            long turn, long step, Message message, TokenUsage usage
    ) {
        return new SessionEvent.AssistantMessage(0, now(),
                new AssistantMessagePayload(turn, step, message, usage),
                new SurfaceIntent(SurfaceOp.append(), null));
    }

    // ── Tool calls ───────────────────────────────────────────────

    /**
     * 转换为 ol Call。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param turn 当前回合。
     * @param step 当前步数。
     * @param callId call ID参数。
     * @param name 名称。
     * @param arguments arguments参数。
     * @return 转换后的目标对象。
     */
    public static SessionEvent.ToolCall toolCall(long turn, long step, String callId, String name, String arguments) {
        return new SessionEvent.ToolCall(0, now(),
                new ToolCallPayload(turn, step, callId, name, arguments));
    }

    /**
     * 创建工具结果事件。
     * <p>流程：封装回合/步数、模型消息、错误和元数据 → 返回可追加的事件。</p>
     */
    public static SessionEvent.ToolResult toolResult(
            long turn, long step, Message message,
            Map<String, Object> error, Map<String, Object> meta
    ) {
        return new SessionEvent.ToolResult(0, now(),
                new ToolResultPayload(turn, step, message, error, meta),
                new SurfaceIntent(SurfaceOp.append(), null));
    }

    // ── Todo ─────────────────────────────────────────────────────

    /**
     * 转换为 do Write。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param todos todos参数。
     * @return 转换后的目标对象。
     */
    public static SessionEvent.TodoWrite todoWrite(List<TodoItemPayload> todos) {
        return new SessionEvent.TodoWrite(0, now(), new TodoWritePayload(todos));
    }

    // ── Request metadata ─────────────────────────────────────────

    /**
     * 创建请求头事件。
     * <p>流程：固化模型配置、适配器默认值、系统提示词和工具列表 → 供会话回放使用。</p>
     */
    public static SessionEvent.RequestHeader requestHeader(
            Map<String, Object> config, Map<String, Object> adapterDefaults,
            String system, List<Map<String, Object>> tools
    ) {
        return new SessionEvent.RequestHeader(0, now(),
                new RequestHeaderPayload(config, adapterDefaults, system, tools));
    }

    /**
     * 处理请求 上下文。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param provider 模型提供方。
     * @param model 模型设置。
     * @param contextWindow 上下文 Window参数。
     * @return 方法执行结果。
     */
    public static SessionEvent.RequestContext requestContext(String provider, String model, Long contextWindow) {
        return new SessionEvent.RequestContext(0, now(),
                new RequestContextPayload(provider, model, contextWindow));
    }

    // ── Session lifecycle ────────────────────────────────────────

    /**
     * 处理会话 End Seed。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @return 方法执行结果。
     */
    public static SessionEvent.SessionEndSeed sessionEndSeed() {
        return new SessionEvent.SessionEndSeed(0, now());
    }

    // ── Agent inbox ──────────────────────────────────────────────

    /**
     * 创建 Agent Inbox 拼接事件。
     * <p>流程：记录目标、起始位置、删除数量、插入消息和结果。</p>
     */
    public static SessionEvent.AgentInboxSpliced inboxSpliced(
            String target, int start, int removedCount,
            List<Message> inserted, String outcome
    ) {
        return new SessionEvent.AgentInboxSpliced(0, now(),
                new InboxSplicedPayload(target, start, removedCount, inserted, outcome));
    }

    // ── Plan mode ────────────────────────────────────────────────

    /**
     * 处理计划 模式 Enter。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @return 方法执行结果。
     */
    public static SessionEvent.PlanModeChange planModeEnter() {
        return new SessionEvent.PlanModeChange(0, now(), new PlanModePayload("enter", null));
    }

    /**
     * 处理计划 模式 Exit。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param plan 计划参数。
     * @return 方法执行结果。
     */
    public static SessionEvent.PlanModeChange planModeExit(String plan) {
        return new SessionEvent.PlanModeChange(0, now(), new PlanModePayload("exit", plan));
    }

    // ── Generic ──────────────────────────────────────────────────

    /**
     * 创建通用扩展事件。
     * @param type 事件类型。
     * @param payload 事件载荷。
     * @return 通用事件。
     */
    public static SessionEvent.Generic generic(SessionEventType type, String payload) {
        return new SessionEvent.Generic(0, now(), type, payload);
    }

    // ── Turn end reason helpers ──────────────────────────────────

    /**
     * 创建正常完成原因。
     * @return 正常完成原因。
     */
    public static TurnEndReason completed() { return new TurnEndReason.Completed(); }
    /**
     * 处理max Tokens。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @return 方法执行结果。
     */
    public static TurnEndReason maxTokens() { return new TurnEndReason.MaxTokens(); }
    /**
     * 处理blocked。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @return 方法执行结果。
     */
    public static TurnEndReason blocked() { return new TurnEndReason.Blocked(); }
    /**
     * 处理aborted。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param reason reason参数。
     * @return 方法执行结果。
     */
    public static TurnEndReason aborted(String reason) { return new TurnEndReason.Aborted(reason); }
    /**
     * 处理error。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param message 消息内容或对象。
     * @return 方法执行结果。
     */
    public static TurnEndReason error(String message) { return new TurnEndReason.Error(message, "UNKNOWN"); }
    /**
     * 处理error。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param message 消息内容或对象。
     * @param code 编码参数。
     * @return 方法执行结果。
     */
    public static TurnEndReason error(String message, String code) { return new TurnEndReason.Error(message, code); }
    /**
     * 处理interrupted。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @return 方法执行结果。
     */
    public static TurnEndReason interrupted() { return new TurnEndReason.Interrupted(); }
}
