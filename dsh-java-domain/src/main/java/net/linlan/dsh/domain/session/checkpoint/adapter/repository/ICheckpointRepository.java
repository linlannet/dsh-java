package net.linlan.dsh.domain.session.checkpoint.adapter.repository;

import net.linlan.dsh.domain.session.checkpoint.model.valobj.CheckpointSnapshot;

import java.util.List;
import java.util.Optional;

public interface ICheckpointRepository {
    void save(CheckpointSnapshot snapshot);
    Optional<CheckpointSnapshot> loadLatest(String sessionId);
    List<CheckpointSnapshot> loadAll(String sessionId);
}
