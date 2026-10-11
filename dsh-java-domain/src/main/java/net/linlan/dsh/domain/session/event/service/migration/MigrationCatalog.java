package net.linlan.dsh.domain.session.event.service.migration;

import net.linlan.dsh.domain.session.event.model.entity.SessionLog;
import net.linlan.dsh.domain.session.event.model.valobj.SessionFormatVersion;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * MigrationCatalog 管理会话格式迁移链。
 * <p>维护一组按版本号排序的迁移步骤，{@link #migrateToCurrent(SessionLog)}
 * 依次执行从当前版本到 {@link SessionFormatVersion#CURRENT} 的所有迁移。
 * <p>线程安全：迁移链在构造后不可变（{@link #register} 返回新实例）。
 */
public final class MigrationCatalog {

    private final List<SessionFormatMigration> chain;

    private MigrationCatalog(List<SessionFormatMigration> chain) {
        this.chain = List.copyOf(chain);
    }

    /**
     * 创建包含默认迁移链（V0→V1→V2→V3→V4）的 catalog。
     */
    public static MigrationCatalog defaultChain() {
        return new MigrationCatalog(List.of(
                new V0ToV1Migration(),
                new V1ToV2Migration(),
                new V2ToV3Migration(),
                new V3ToV4Migration()
        ));
    }

    /**
     * 运行时注册额外迁移，返回新 catalog（不可变）。
     */
    public MigrationCatalog register(SessionFormatMigration migration) {
        Objects.requireNonNull(migration, "migration");
        var extended = new ArrayList<SessionFormatMigration>(chain);
        extended.add(migration);
        extended.sort(java.util.Comparator.comparingInt(SessionFormatMigration::fromVersion));
        return new MigrationCatalog(extended);
    }

    /**
     * 将会话迁移到当前格式版本。
     * <p>依次执行迁移链中 fromVersion >= session.formatVersion 的所有迁移。
     * @param input 待迁移的会话日志
     * @return 迁移后的会话日志（formatVersion == {@link SessionFormatVersion#CURRENT}）
     * @throws UnsupportedFormatException 如果 session.formatVersion > CURRENT
     */
    public SessionLog migrateToCurrent(SessionLog input) {
        Objects.requireNonNull(input, "input");
        int currentVersion = input.header().formatVersion();

        if (currentVersion > SessionFormatVersion.CURRENT) {
            throw new UnsupportedFormatException(currentVersion);
        }

        if (currentVersion == SessionFormatVersion.CURRENT) {
            return input;
        }

        SessionLog result = input;
        for (var migration : chain) {
            if (migration.fromVersion() >= currentVersion && migration.toVersion() <= SessionFormatVersion.CURRENT) {
                result = migration.migrate(result);
                currentVersion = result.header().formatVersion();
            }
        }
        return result;
    }

    /**
     * 返回迁移链中的迁移步骤数。
     */
    public int chainSize() {
        return chain.size();
    }
}
