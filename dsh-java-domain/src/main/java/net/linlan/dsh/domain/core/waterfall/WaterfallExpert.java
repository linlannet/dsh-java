package net.linlan.dsh.domain.core.waterfall;

import net.linlan.dsh.domain.core.waterfall.model.valobj.WaterfallEvent;
import net.linlan.dsh.domain.core.waterfall.model.valobj.WaterfallOutcome;

/**
 * WaterfallExpert 是 waterfall 链中的单个专家。
 *
 * @param <T> 处理的值类型
 */
public interface WaterfallExpert<T> {

    /**
     * 优先级（升序，值越小越先执行）。
     */
    int priority();

    /**
     * 此专家是否可以终止链。
     * <p>当返回 Terminate 时：true 则链终止，false 则降级为 Continue。</p>
     */
    boolean canTerminate();

    /**
     * 处理事件。
     *
     * @param event   带上下文的事件
     * @param current 前序专家的输出（首位专家接收初始值）
     * @return 处理结果（Continue 或 Terminate）
     */
    WaterfallOutcome<T> handle(WaterfallEvent<T> event, T current);
}
