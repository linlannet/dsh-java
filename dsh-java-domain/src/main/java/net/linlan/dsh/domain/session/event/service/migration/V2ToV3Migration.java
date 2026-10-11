package net.linlan.dsh.domain.session.event.service.migration;

import net.linlan.dsh.domain.session.event.model.entity.SessionLog;

/**
 * V2 → V3 迁移：补充 agentPreset 字段。
 * <p>V2 会话可能缺少 agentPreset（决定工具和提示词的预设标识），
 * 迁移后默认为 null。纯函数：输入不被修改，输出新对象。
 */
public final class V2ToV3Migration implements SessionFormatMigration {

    @Override
    public int fromVersion() {
        return 2;
    }

    @Override
    public int toVersion() {
        return 3;
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
