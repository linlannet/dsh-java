package net.linlan.dsh.domain.document.office.model.valobj;

/**
 * OfficeFormat 枚举支持的 Office 文档格式。
 *
 */
public enum OfficeFormat {
    DOC, DOCX, XLS, XLSX, PPT, PPTX;

    public boolean isOoxml() {
        return this == DOCX || this == XLSX || this == PPTX;
    }

    public static OfficeFormat fromExtension(String ext) {
        if (ext == null) return null;
        return switch (ext.toLowerCase()) {
            case "doc" -> DOC;
            case "docx" -> DOCX;
            case "xls" -> XLS;
            case "xlsx" -> XLSX;
            case "ppt" -> PPT;
            case "pptx" -> PPTX;
            default -> null;
        };
    }
}
