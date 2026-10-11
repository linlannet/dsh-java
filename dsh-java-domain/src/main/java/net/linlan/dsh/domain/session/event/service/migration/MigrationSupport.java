package net.linlan.dsh.domain.session.event.service.migration;

import net.linlan.dsh.domain.session.event.model.entity.SessionHeader;
import net.linlan.dsh.domain.session.event.model.entity.SessionLog;

/**
 * 迁移辅助工具，提供各步迁移共享的纯函数逻辑。
 */
final class MigrationSupport {

    private MigrationSupport() {}

    /**
     * 创建带新版本号的 SessionHeader，保留所有其他字段。
     */
    static SessionHeader withVersion(SessionHeader header, int newVersion, int newFormatVersion) {
        return new SessionHeader(
                newVersion,
                header.sessionId(),
                header.createdAt(),
                header.cwd(),
                header.parentSession(),
                header.seedLength(),
                header.origin(),
                header.delegationDepth(),
                header.agentPreset(),
                newFormatVersion
        );
    }

    /**
     * 创建新 SessionLog 并复制所有事件（纯函数：输入不被修改）。
     */
    static SessionLog copyToNew(SessionLog source, SessionHeader newHeader) {
        var migrated = new SessionLog(source.sessionId(), newHeader);
        var events = source.events();
        if (!events.isEmpty()) {
            migrated.appendBatch(events);
        }
        return migrated;
    }
}
