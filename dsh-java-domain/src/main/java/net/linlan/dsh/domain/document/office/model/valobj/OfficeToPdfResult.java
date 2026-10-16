package net.linlan.dsh.domain.document.office.model.valobj;

import java.util.List;
import java.util.Objects;

/**
 * OfficeToPdfResult 表示一次 Office 转 PDF 结果。
 *
 */
public record OfficeToPdfResult(
        byte[] pdfBytes,
        List<String> missingFonts
) {
    public OfficeToPdfResult {
        Objects.requireNonNull(missingFonts, "missingFonts 不能为空");
    }

    public static OfficeToPdfResult of(byte[] pdfBytes) {
        return new OfficeToPdfResult(pdfBytes, List.of());
    }

    public static OfficeToPdfResult of(byte[] pdfBytes, List<String> missingFonts) {
        return new OfficeToPdfResult(pdfBytes, missingFonts);
    }
}
