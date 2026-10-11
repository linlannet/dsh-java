package net.linlan.dsh.domain.tool.model.valobj;

import net.linlan.dsh.domain.model.entity.ToolExecutionResult;

import java.util.function.Supplier;

/**
 * AroundWrapper 是 AROUND 阶段的环绕包装器。
 * <p>洋葱模型：包装器可控制是否调用 {@code proceed}（工具主体），可在调用前后注入逻辑。</p>
 */
public interface AroundWrapper {

    /**
     * 包装工具主体。
     *
     * @param proceed 调用工具主体的回调
     * @return 工具执行结果
     */
    ToolExecutionResult wrap(Supplier<ToolExecutionResult> proceed);

    /**
     * 直通包装器（不干预，直接调用主体）。
     */
    static AroundWrapper passThrough() {
        return Supplier::get;
    }
}
