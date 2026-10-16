package net.linlan.dsh.domain.document.office.model.valobj;

import java.util.Objects;

/**
 * OfficeToPdfRequest 表示一次 Office 转 PDF 请求。
 *
 */
public record OfficeToPdfRequest(
        byte[] sourceBytes,
        OfficeFormat format,
        OfficeEngine engine,
        int queueBound,
        boolean cacheEnabled
) {
    public OfficeToPdfRequest {
        Objects.requireNonNull(sourceBytes, "sourceBytes 不能为空");
        Objects.requireNonNull(format, "format 不能为空");
        Objects.requireNonNull(engine, "engine 不能为空");
        if (queueBound <= 0) queueBound = 16;
    }

    public static OfficeToPdfRequest of(byte[] sourceBytes, OfficeFormat format) {
        return new OfficeToPdfRequest(sourceBytes, format, OfficeEngine.NATIVE, 16, true);
    }
}
