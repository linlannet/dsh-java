package net.linlan.dsh.domain.runtime.meter.adapter.repository;

import net.linlan.dsh.domain.runtime.meter.model.valobj.TokenSnapshot;

import java.util.Optional;

/**
 * ITokenSnapshotRepository 是 Token 快照的仓储端口。
 * <p>domain 定义端口契约，infrastructure 提供实现。</p>
 */
public interface ITokenSnapshotRepository {

    Optional<TokenSnapshot> load(String sessionId);

    void save(String sessionId, TokenSnapshot snapshot);
}
