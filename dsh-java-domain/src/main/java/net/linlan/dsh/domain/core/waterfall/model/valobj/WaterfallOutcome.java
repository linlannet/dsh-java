package net.linlan.dsh.domain.core.waterfall.model.valobj;

/**
 * WaterfallOutcome 是单个专家处理后的结果。
 * <p>Continue 表示继续传递给下一位专家；Terminate 表示终止链。</p>
 * <p>仅当专家 {@code canTerminate()=true} 时 Terminate 才真正终止链；
 * 否则降级为 Continue（输出仍传递）。</p>
 *
 * @param <T> 输出类型
 */
public sealed interface WaterfallOutcome<T> permits WaterfallOutcome.Continue, WaterfallOutcome.Terminate {

    /**
     * 当前专家的输出值。
     */
    T output();

    /**
     * 继续传递给下一位专家。
     */
    record Continue<T>(T output) implements WaterfallOutcome<T> {}

    /**
     * 终止链（仅当专家 canTerminate=true 时生效）。
     */
    record Terminate<T>(T output) implements WaterfallOutcome<T> {}

    /**
     * 创建 Continue 结果。
     */
    static <T> Continue<T> continueWith(T output) {
        return new Continue<>(output);
    }

    /**
     * 创建 Terminate 结果。
     */
    static <T> Terminate<T> terminateWith(T output) {
        return new Terminate<>(output);
    }
}
