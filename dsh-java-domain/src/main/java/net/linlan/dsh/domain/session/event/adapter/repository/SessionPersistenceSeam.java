package net.linlan.dsh.domain.session.event.adapter.repository;

/**
 * SessionPersistenceSeam 是会话持久化缝口标记接口。
 * <p>继承 {@link ISessionEventStore} 的全部契约，不添加新方法。
 * <p>用途：区分「正式持久化适配器」（Jsonl/Jdbc）与「临时 stub/内存实现」，
 * 使 {@code SessionEventLogService} 只依赖正式持久化实现，防止测试 stub 误注入生产路径。
 * <p>契约文档：实现方必须满足 {@link ISessionEventStore} 全部方法语义，
 * 且 {@code saveHeader} 幂等、{@code append} 追加不覆盖、{@code readAll} 返回追加顺序。
 *
 * @see ISessionEventStore
 */
public interface SessionPersistenceSeam extends ISessionEventStore {
}
