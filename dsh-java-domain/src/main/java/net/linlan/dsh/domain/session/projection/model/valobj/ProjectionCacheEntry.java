package net.linlan.dsh.domain.session.projection.model.valobj;

/**
 * ProjectionCacheEntry 表示投影缓存的条目。
 *
 * @param state 投影状态
 * @param lastEventStepIndex 最后合并的事件步骤索引
 * @param <T> 投影状态类型
 */
public record ProjectionCacheEntry<T>(T state, long lastEventStepIndex) {

    public static <T> ProjectionCacheEntry<T> initial(T initialState) {
        return new ProjectionCacheEntry<>(initialState, -1);
    }

    public ProjectionCacheEntry<T> update(T newState, long newStepIndex) {
        return new ProjectionCacheEntry<>(newState, newStepIndex);
    }
}
