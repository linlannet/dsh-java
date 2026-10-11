package net.linlan.dsh.domain.session.event.service.migration;

import net.linlan.dsh.domain.session.event.model.entity.SessionLog;

/**
 * SessionFormatMigration 是会话格式迁移的冻结纯函数接口。
 * <p>每个实现负责一个版本到下一个版本的迁移（如 V0→V1、V1→V2 等）。
 * <p>契约：
 * <ul>
 *   <li>纯函数：输入不被修改，返回新对象</li>
 *   <li>仅升级不降级：{@code toVersion() > fromVersion()}</li>
 *   <li>幂等：对已是 {@code toVersion()} 的输入迁移无副作用</li>
 * </ul>
 */
public interface SessionFormatMigration {

    /**
     * 返回源版本号。
     */
    int fromVersion();

    /**
     * 返回目标版本号。
     */
    int toVersion();

    /**
     * 执行迁移。
     * <p>输入 {@code session} 不被修改，返回迁移后的新 {@link SessionLog}。
     * @param session 待迁移的会话日志
     * @return 迁移后的会话日志
     */
    SessionLog migrate(SessionLog session);
}
