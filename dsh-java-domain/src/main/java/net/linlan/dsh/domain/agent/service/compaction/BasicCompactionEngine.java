package net.linlan.dsh.domain.agent.service.compaction;

import net.linlan.dsh.domain.agent.model.entity.AgentOptions;
import net.linlan.dsh.domain.model.entity.Message;
import net.linlan.dsh.domain.model.entity.MessageSource;
import net.linlan.dsh.domain.model.valobj.ContentBlock;
import net.linlan.dsh.domain.llm.model.valobj.GenerateOptions;
import net.linlan.dsh.domain.model.valobj.TextBlock;
import net.linlan.dsh.domain.llm.service.LlmGenerationService;
import net.linlan.dsh.domain.session.event.model.entity.SessionEvent;
import net.linlan.dsh.domain.session.event.model.entity.SessionLog;
import net.linlan.dsh.domain.session.event.model.valobj.SurfaceIntent;
import net.linlan.dsh.domain.session.event.model.valobj.SurfaceOp;
import net.linlan.dsh.domain.session.event.service.SessionEventFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 基础压缩引擎。
 * <p>
 * 执行流程：
 * 1. 判断消息量和上下文压力是否达到压缩条件；
 * 2. 选取需要压缩的消息区间并请求大模型总结；
 * 3. 用一条摘要消息替换原始区间，减少上下文长度。
 *
 * <p>调用流程：模型输出 → 工具调用 → 结果回填 → 下一步模型调用；每一步写入会话事件。</p>
 * <p>示例：模型请求执行 fs_read 后，工具结果作为观察值进入下一轮模型输入。</p>
 *
 */
public class BasicCompactionEngine implements CompactionEngine {

    private static final Logger log = LoggerFactory.getLogger(BasicCompactionEngine.class);

    private final LlmGenerationService llmGenerationService;
    private final long pressureThresholdTokens;
    private final int minMessagesToCompact;

    /**
     * 初始化基础会话压缩引擎。
     * <p>流程：读取历史消息 → 按 token 预算裁剪 → 返回压缩后的上下文。</p>
     */
    public BasicCompactionEngine(
            LlmGenerationService llmGenerationService,
            long pressureThresholdTokens,
            int minMessagesToCompact
    ) {
        this.llmGenerationService = llmGenerationService;
        this.pressureThresholdTokens = pressureThresholdTokens;
        this.minMessagesToCompact = minMessagesToCompact;
    }

    /**
     * compact Now。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param session session参数。
     * @param options Agent 配置。
     * @return 方法执行结果。
     */

    /**
     * compact Now。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param session session参数。
     * @param options Agent 配置。
     * @return 方法执行结果。
     */

    @Override
    public CompletableFuture<CompactionResult> compactIfNeeded(
            SessionLog session,
            AgentOptions options,
            CompactionTrigger trigger
    ) {
        List<Message> messages = session.deriveMessages();
        if (messages.size() < minMessagesToCompact) {
            return CompletableFuture.completedFuture(null);
        }

        // 估算令牌数（粗略按 4 个字符约等于 1 个令牌）
        long estimatedTokens = messages.stream()
                .mapToLong(m -> m.content().stream()
                        .mapToLong(b -> b instanceof TextBlock tb ? tb.text().length() / 4 : 10)
                        .sum())
                .sum();

        if (trigger != CompactionTrigger.CONTEXT_OVERFLOW && estimatedTokens < pressureThresholdTokens) {
            return CompletableFuture.completedFuture(null);
        }

        log.info("[Compaction] 会话={} 触发压缩（trigger={} 消息数={} 估算token={} 阈值={}）",
                session.sessionId(), trigger, messages.size(), estimatedTokens, pressureThresholdTokens);
        return compactNow(session, options);
    }

    /**
     * compact Now。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param session session参数。
     * @param options Agent 配置。
     * @return 方法执行结果。
     */

    @Override
    public CompletableFuture<CompactionResult> compactNow(
            SessionLog session,
            AgentOptions options
    ) {
        List<Message> messages = session.deriveMessages();
        if (messages.size() < 2) {
            return CompletableFuture.completedFuture(null);
        }

        // 选择压缩区间：保留最近约四分之一的消息，其余进行压缩
        int keepRecent = Math.max(1, messages.size() / 4);
        int compactEnd = messages.size() - keepRecent;
        if (compactEnd < 2) {
            return CompletableFuture.completedFuture(null);
        }

        List<Message> toCompact = messages.subList(0, compactEnd);
        List<Message> toKeep = messages.subList(compactEnd, messages.size());

        // 构建摘要请求
        StringBuilder summaryInput = new StringBuilder();
        for (Message msg : toCompact) {
            String role = msg.role();
            for (ContentBlock block : msg.content()) {
                if (block instanceof TextBlock tb) {
                    summaryInput.append(role).append(": ").append(tb.text()).append('\n');
                }
            }
        }

        String systemPrompt = "Summarize the following conversation history concisely. " +
                "Preserve key decisions, context, and any open questions. " +
                "The summary will replace the original messages in the session.";

        Message summaryRequest = Message.createUser(
                List.of(new TextBlock(summaryInput.toString())),
                new MessageSource.UserSource()
        );

        GenerateOptions genOpts = new GenerateOptions(
                options.provider(),
                options.model(),
                null,
                List.of(summaryRequest),
                systemPrompt,
                List.of(),
                null,
                null,
                null,
                session.sessionId(),
                "compaction",
                options.baseUrl(),
                options.apiKey(),
                options.protocol()
        );

        // 调用大模型并收集摘要
        return llmGenerationService.generate(genOpts).thenCompose(summaryResponse -> {
            if (summaryResponse == null) {
                return CompletableFuture.completedFuture(null);
            }

            String summary = summaryResponse.content().stream()
                    .filter(b -> b instanceof TextBlock)
                    .map(b -> ((TextBlock) b).text())
                    .reduce((a, b) -> a + "\n" + b)
                    .orElse("");

            if (summary.isBlank()) {
                return CompletableFuture.completedFuture(null);
            }

            // 构造摘要消息
            Message summaryMsg = Message.createUser(
                    List.of(new TextBlock("[Compacted History]\n\n" + summary)),
                    new MessageSource.UserSource()
            );

            // 估算压缩前后的令牌节省量
            long tokensBefore = toCompact.stream()
                    .mapToLong(m -> m.content().stream()
                            .mapToLong(b -> b instanceof TextBlock tb ? tb.text().length() / 4 : 10)
                            .sum())
                    .sum();
            long tokensAfter = summary.length() / 4;

            // 通过替换操作记录压缩动作，覆盖原始区间
            // 原始实现会追加 replace(start, end) 的事件
            // 这里先以替换意图写入，后续由 SessionLog 完成投影

            // 查找需要替换的事件序列范围
            List<SessionEvent> events = session.events();
            long startSeq = -1;
            long endSeq = -1;
            int msgIdx = 0;
            for (SessionEvent event : events) {
                if (!event.isSurfaceEvent()) continue;
                if (msgIdx >= compactEnd) break;
                SurfaceOp op = event.surfaceIntent() != null
                        ? event.surfaceIntent().surfaceOp()
                        : null;
                if (op instanceof SurfaceOp.Append) {
                    if (startSeq < 0) startSeq = event.seq();
                    endSeq = event.seq();
                    msgIdx++;
                }
            }

            if (startSeq < 0) {
                log.warn("[Compaction] 未能定位需要替换的事件区间，放弃压缩");
                return CompletableFuture.completedFuture(null);
            }

            log.info("[Compaction] 压缩完成：压缩 {} 条消息为 1 条摘要，token {}→{}（节省 {}），seq范围=[{},{}]",
                    toCompact.size(), tokensBefore, tokensAfter, tokensBefore - tokensAfter, startSeq, endSeq);

            // 追加摘要替换事件
            // 使用用户消息搭配替换操作
            // 会话日志追加方法会自动分配序号
            session.append(SessionEventFactory.userMessage(
                    0, 0, summaryMsg,
                    new SurfaceIntent(
                            new SurfaceOp.Replace(startSeq, endSeq), null
                    )
            ));

            return CompletableFuture.completedFuture(new CompactionResult(
                    summary,
                    startSeq,
                    endSeq,
                    tokensBefore,
                    tokensAfter,
                    toCompact,
                    summaryMsg
            ));
        });
    }
}
