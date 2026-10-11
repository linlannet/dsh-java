package net.linlan.dsh.domain.session.event.model.valobj;

/**
 * SurfaceOp 表示SurfaceOp。
 */
public sealed interface SurfaceOp permits SurfaceOp.Append, SurfaceOp.Replace {

    /**
     * Append 表示Append。
     */
    record Append() implements SurfaceOp {}

    /**
     * Replace 表示Replace。
     */
    record Replace(long start, long end) implements SurfaceOp {
        public Replace {
            if (start < 0 || end < 0 || end < start) {
                throw new IllegalArgumentException("Invalid replace range: start=" + start + ", end=" + end);
            }
        }
    }

    /**
     * 处理append。
     * @return 领域处理结果。
     */
    static SurfaceOp append() {
        return new Append();
    }

    /**
     * 处理replace。
     * <p>流程：校验入参 → 执行领域动作 → 返回处理结果。</p>
     * @param start start。
     * @return 领域处理结果。
     */
    static SurfaceOp replace(long start, long end) {
        return new Replace(start, end);
    }
}
