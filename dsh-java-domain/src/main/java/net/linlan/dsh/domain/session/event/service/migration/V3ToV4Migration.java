package net.linlan.dsh.domain.session.event.service.migration;

import net.linlan.dsh.domain.session.event.model.entity.SessionLog;

/**
 * V3 → V4 迁移：引入 Scope 事件分发与五阶段工具管线。
 * <p>V4 格式支持 Scope 路由事件和 ToolPipeline 拦截器，
 * 迁移后 formatVersion 设为 4。纯函数：输入不被修改，输出新对象。
 */
public final class V3ToV4Migration implements SessionFormatMigration {

    @Override
    public int fromVersion() {
        return 3;
    }

    @Override
    public int toVersion() {
        return 4;
    }

    @Override
    public SessionLog migrate(SessionLog session) {
        if (session.header().formatVersion() >= toVersion()) {
            return session;
        }
        var newHeader = MigrationSupport.withVersion(
                session.header(), session.header().version(), toVersion());
        return MigrationSupport.copyToNew(session, newHeader);
    }
}
