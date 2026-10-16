package net.linlan.dsh.domain.session.checkpoint.model.valobj;

import java.util.Objects;
import java.util.Optional;

/**
 * CheckpointRollback 表示检查点回滚结果，包含目标快照与回滚状态。
 */
public record CheckpointRollback(
        CheckpointSnapshot targetSnapshot,
        boolean success,
        String message
) {
    public CheckpointRollback {
        Objects.requireNonNull(message, "message 不能为空");
    }

    public static CheckpointRollback success(CheckpointSnapshot snapshot) {
        return new CheckpointRollback(snapshot, true, "回滚成功");
    }

    public static CheckpointRollback notFound(String detail) {
        return new CheckpointRollback(null, false, "检查点不存在: " + detail);
    }

    public Optional<CheckpointSnapshot> target() {
        return Optional.ofNullable(targetSnapshot);
    }
}
