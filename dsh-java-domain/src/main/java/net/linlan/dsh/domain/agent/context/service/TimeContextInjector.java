package net.linlan.dsh.domain.agent.context.service;

import net.linlan.dsh.domain.agent.service.ScopedSystemPromptAssembler;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * TimeContextInjector 每步注入当前时间戳和自 Session 起始的 elapsed 时间。
 * <p>经 {@link ScopedSystemPromptAssembler#registerContext} 注册为 {@code time} context。
 */
public final class TimeContextInjector {

    private static final String CONTEXT_NAME = "time";
    private static final int CONTEXT_ORDER = 50;

    private final Supplier<Instant> clock;
    private final Instant sessionStart;

    public TimeContextInjector() {
        this(Instant::now, Instant.now());
    }

    public TimeContextInjector(Supplier<Instant> clock, Instant sessionStart) {
        if (clock == null) {
            throw new IllegalArgumentException("clock must not be null");
        }
        this.clock = clock;
        this.sessionStart = sessionStart == null ? Instant.now() : sessionStart;
    }

    /**
     * 生成当前时间上下文文本。
     *
     * @return 时间上下文文本
     */
    public String inject() {
        Instant now = clock.get();
        long elapsedMs = now.toEpochMilli() - sessionStart.toEpochMilli();
        String timestamp = DateTimeFormatter.ISO_INSTANT.format(now.atZone(ZoneId.systemDefault()));
        return "Current time: " + timestamp + "\nElapsed since session start: " + elapsedMs + "ms";
    }

    /**
     * 注册到 ScopedSystemPromptAssembler。
     *
     * @param assembler 系统提示组装器
     */
    public void register(ScopedSystemPromptAssembler assembler) {
        if (assembler == null) {
            return;
        }
        assembler.registerContext(new ScopedSystemPromptAssembler.PromptContext(
                CONTEXT_NAME,
                CONTEXT_ORDER,
                (Function<ScopedSystemPromptAssembler.AssembleContext, String>) ctx -> inject()
        ));
    }

    /**
     * 返回上下文名称。
     */
    public String contextName() {
        return CONTEXT_NAME;
    }
}
