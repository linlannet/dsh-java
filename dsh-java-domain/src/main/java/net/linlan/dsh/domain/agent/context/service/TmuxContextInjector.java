package net.linlan.dsh.domain.agent.context.service;

import net.linlan.dsh.domain.agent.service.ScopedSystemPromptAssembler;

import java.util.function.Supplier;

/**
 * TmuxContextInjector 每步注入当前 tmux pane/window 位置信息。
 * <p>经 {@link ScopedSystemPromptAssembler#registerContext} 注册为 {@code tmux} context。
 * {@code @ConditionalOnProperty(name="dsh.tmux.enabled")} 按配置激活。
 */
public final class TmuxContextInjector {

    private static final String CONTEXT_NAME = "tmux";
    private static final int CONTEXT_ORDER = 51;

    private final Supplier<String> tmuxInfoProvider;

    public TmuxContextInjector() {
        this(() -> defaultTmuxInfo());
    }

    public TmuxContextInjector(Supplier<String> tmuxInfoProvider) {
        if (tmuxInfoProvider == null) {
            throw new IllegalArgumentException("tmuxInfoProvider must not be null");
        }
        this.tmuxInfoProvider = tmuxInfoProvider;
    }

    /**
     * 生成当前 tmux 上下文文本。
     *
     * @return tmux 上下文文本
     */
    public String inject() {
        String info = tmuxInfoProvider.get();
        if (info == null || info.isBlank()) {
            return "tmux: not available";
        }
        return "tmux: " + info;
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
                (java.util.function.Function<ScopedSystemPromptAssembler.AssembleContext, String>) ctx -> inject()
        ));
    }

    /**
     * 返回上下文名称。
     */
    public String contextName() {
        return CONTEXT_NAME;
    }

    private static String defaultTmuxInfo() {
        String tmux = System.getenv("TMUX");
        if (tmux == null || tmux.isEmpty()) {
            return "";
        }
        return "pane=" + tmux;
    }
}
