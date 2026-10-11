package net.linlan.dsh.domain.session.event.service.migration;

import net.linlan.dsh.domain.session.event.model.entity.SessionLog;

/**
 * V1 → V2 迁移：补充 delegationDepth 字段。
 * <p>V1 会话可能缺少 delegationDepth（子代理递归深度预算），
 * 迁移后默认为 0。纯函数：输入不被修改，输出新对象。
 */
public final class V1ToV2Migration implements SessionFormatMigration {

    @Override
    public int fromVersion() {
        return 1;
    }

    @Override
    public int toVersion() {
        return 2;
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
