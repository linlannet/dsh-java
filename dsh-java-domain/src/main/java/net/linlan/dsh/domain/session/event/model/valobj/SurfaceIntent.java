package net.linlan.dsh.domain.session.event.model.valobj;

import java.util.List;
import java.util.Objects;

/**
 * SurfaceIntent 表示SurfaceIntent。
 */
public record SurfaceIntent(
        SurfaceOp surfaceOp,
        List<Long> sourceEventSeqs
) {
    public SurfaceIntent {
        Objects.requireNonNull(surfaceOp, "surfaceOp");
        sourceEventSeqs = sourceEventSeqs == null ? List.of() : List.copyOf(sourceEventSeqs);
    }
}
