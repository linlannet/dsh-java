package net.linlan.dsh.domain.core.waterfall.model.valobj;

import java.util.List;
import java.util.Map;

/**
 * WaterfallContext 是 waterfall 执行链中当前专家的上下文。
 *
 * @param currentExpertIndex  当前专家在链中的序号（从 0 开始）
 * @param executedExperts     已执行的专家名称列表
 * @param metadata            附加元数据
 */
public record WaterfallContext(int currentExpertIndex, List<String> executedExperts, Map<String, Object> metadata) {

    public WaterfallContext {
        executedExperts = executedExperts == null ? List.of() : List.copyOf(executedExperts);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    /**
     * 创建空的初始上下文。
     */
    public static WaterfallContext initial() {
        return new WaterfallContext(0, List.of(), Map.of());
    }
}
