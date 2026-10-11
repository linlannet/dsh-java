package net.linlan.dsh.domain.session.event.service.migration;

import net.linlan.dsh.domain.session.event.model.entity.SessionLog;

/**
 * V0 → V1 迁移：初始格式规范化。
 * <p>处理 V0 会话缺少 origin/parentSession 字段的结构差异，
 * 统一为 V1 格式。纯函数：输入不被修改，输出新对象。
 */
public final class V0ToV1Migration implements SessionFormatMigration {

    @Override
    public int fromVersion() {
        return 0;
    }

    @Override
    public int toVersion() {
        return 1;
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
