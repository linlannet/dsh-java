package net.linlan.dsh.domain.document.office.model.valobj;

/**
 * OfficeEngine 枚举转换引擎类型。
 *
 * <p>目标声明原生引擎时使用原生 LibreOffice，其余使用 WASM。</p>
 *
 */
public enum OfficeEngine {
    NATIVE, WASM
}
