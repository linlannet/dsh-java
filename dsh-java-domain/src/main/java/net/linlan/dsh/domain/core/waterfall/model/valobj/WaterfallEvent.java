package net.linlan.dsh.domain.core.waterfall.model.valobj;

/**
 * WaterfallEvent 是 waterfall 链中传递的事件。
 *
 * @param type    事件类型
 * @param payload 事件负载
 * @param context 当前专家的执行上下文（由链在调用前填充）
 * @param <T>     负载类型
 */
public record WaterfallEvent<T>(String type, T payload, WaterfallContext context) {

    public WaterfallEvent {
        if (type == null || type.isEmpty()) {
            throw new IllegalArgumentException("type must not be empty");
        }
        if (context == null) {
            context = WaterfallContext.initial();
        }
    }

    /**
     * 创建不带上下文的事件（链在执行时填充 context）。
     */
    public static <T> WaterfallEvent<T> of(String type, T payload) {
        return new WaterfallEvent<>(type, payload, WaterfallContext.initial());
    }
}
