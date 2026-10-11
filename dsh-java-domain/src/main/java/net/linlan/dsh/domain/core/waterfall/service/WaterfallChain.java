package net.linlan.dsh.domain.core.waterfall.service;

import net.linlan.dsh.domain.core.waterfall.WaterfallExpert;
import net.linlan.dsh.domain.core.waterfall.model.valobj.WaterfallContext;
import net.linlan.dsh.domain.core.waterfall.model.valobj.WaterfallEvent;
import net.linlan.dsh.domain.core.waterfall.model.valobj.WaterfallOutcome;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * WaterfallChain 是 waterfall 拦截链的执行器。
 * <p>构造时按 {@link WaterfallExpert#priority()} 升序排列专家。
 * {@link #execute(WaterfallEvent, Object)} 依次执行专家，每位可读取前序输出 current。</p>
 * <p>终止语义：canTerminate=true 专家返回 Terminate 后，后续专家不调用；
 * canTerminate=false 专家返回 Terminate 时降级为 Continue（不终止，输出仍传递）。</p>
 * <p>线程安全：专家列表用 {@link CopyOnWriteArrayList}，支持运行时注册。</p>
 *
 * @param <T> 链中传递的值类型
 */
public class WaterfallChain<T> {

    private final CopyOnWriteArrayList<WaterfallExpert<T>> experts;

    /**
     * 创建空链。
     */
    public WaterfallChain() {
        this.experts = new CopyOnWriteArrayList<>();
    }

    /**
     * 创建带初始专家的链（按 priority 升序排列）。
     */
    public WaterfallChain(List<WaterfallExpert<T>> experts) {
        var sorted = new ArrayList<>(experts);
        sorted.sort(Comparator.comparingInt(WaterfallExpert::priority));
        this.experts = new CopyOnWriteArrayList<>(sorted);
    }

    /**
     * 运行时注册专家（插入后保持 priority 升序）。
     */
    public void registerExpert(WaterfallExpert<T> expert) {
        var copy = new ArrayList<>(experts);
        copy.add(expert);
        copy.sort(Comparator.comparingInt(WaterfallExpert::priority));
        experts.clear();
        experts.addAll(copy);
    }

    /**
     * 执行 waterfall 链。
     *
     * @param event  触发事件（链会在调用每位专家前填充 context）
     * @param initial 初始值（传给首位专家的 current）
     * @return 链的最终输出
     */
    public T execute(WaterfallEvent<T> event, T initial) {
        T current = initial;
        var executed = new ArrayList<String>();
        var snapshot = List.copyOf(experts);

        for (int i = 0; i < snapshot.size(); i++) {
            var expert = snapshot.get(i);
            var context = new WaterfallContext(i, List.copyOf(executed), Map.of());
            var eventWithContext = new WaterfallEvent<>(event.type(), event.payload(), context);
            var outcome = expert.handle(eventWithContext, current);
            current = outcome.output();
            executed.add(expert.getClass().getSimpleName());

            if (outcome instanceof WaterfallOutcome.Terminate<T> && expert.canTerminate()) {
                break;
            }
        }
        return current;
    }

    /**
     * 返回当前专家数。
     */
    public int expertCount() {
        return experts.size();
    }
}
