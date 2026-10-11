package net.linlan.dsh.domain.session.projection.adapter.port;

import net.linlan.dsh.domain.session.event.model.entity.SessionEvent;
import net.linlan.dsh.domain.session.projection.model.valobj.ProjectionType;

/**
 * ProjectionProvider 是投影提供者端口。
 * <p>每个实现负责一种投影类型的增量计算：
 * 从初始状态开始，逐事件应用 {@link #applyIncremental} 更新投影状态。
 *
 * @param <T> 投影状态类型
 */
public interface ProjectionProvider<T> {

    /**
     * 返回该 provider 负责的投影类型。
     */
    ProjectionType type();

    /**
     * 返回投影状态的 Java 类型。
     */
    Class<T> projectionClass();

    /**
     * 创建初始投影状态。
     */
    T initial();

    /**
     * 增量合并：将事件应用到当前投影状态，返回新状态。
     * <p>纯函数：不修改 {@code current}，返回新对象。
     * @param current 当前投影状态（可为 null，表示初始状态）
     * @param event 待合并的事件
     * @return 合并后的新投影状态
     */
    T applyIncremental(T current, SessionEvent event);
}
