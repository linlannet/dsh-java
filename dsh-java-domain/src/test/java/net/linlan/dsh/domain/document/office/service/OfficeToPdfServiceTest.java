package net.linlan.dsh.domain.document.office.service;

import net.linlan.dsh.domain.document.office.model.valobj.OfficeEngine;
import net.linlan.dsh.domain.document.office.model.valobj.OfficeFormat;
import net.linlan.dsh.domain.document.office.model.valobj.OfficeToPdfRequest;
import net.linlan.dsh.domain.document.office.model.valobj.OfficeToPdfResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("OfficeToPdfService Office 转 PDF")
class OfficeToPdfServiceTest {

    private static final byte[] PDF = new byte[]{(byte) 0xFF, (byte) 0x0D, (byte) 0x0F};

    @Test
    @DisplayName("DOCX 转换成功返回 PDF")
    void convertDocx() {
        var service = new OfficeToPdfService();
        var request = OfficeToPdfRequest.of(new byte[]{1, 2, 3}, OfficeFormat.DOCX);

        OfficeToPdfResult result = service.convert(request, r -> PDF);
        assertNotNull(result.pdfBytes());
        assertTrue(result.missingFonts().isEmpty());
    }

    @Test
    @DisplayName("二进制 Office 格式返回空缺失字体列表")
    void binaryFormatEmptyFonts() {
        var service = new OfficeToPdfService();
        var request = OfficeToPdfRequest.of(new byte[]{1}, OfficeFormat.DOC);

        OfficeToPdfResult result = service.convert(request, r -> PDF);
        assertTrue(result.missingFonts().isEmpty());
    }

    @Test
    @DisplayName("队列满时拒绝新请求")
    void queueFullRejects() {
        var service = new OfficeToPdfService(1);
        var request = OfficeToPdfRequest.of(new byte[]{1}, OfficeFormat.DOCX);

        OfficeToPdfResult result = service.convert(request, r -> PDF);
        assertNotNull(result.pdfBytes());
    }

    @Test
    @DisplayName("缓存命中相同请求")
    void cacheHit() {
        var service = new OfficeToPdfService();
        var request = OfficeToPdfRequest.of(new byte[]{1, 2}, OfficeFormat.XLSX);
        var converter = new java.util.concurrent.atomic.AtomicInteger(0);

        OfficeToPdfResult r1 = service.convert(request, r -> { converter.incrementAndGet(); return PDF; });
        OfficeToPdfResult r2 = service.convert(request, r -> { converter.incrementAndGet(); return PDF; });

        assertArrayEquals(r1.pdfBytes(), r2.pdfBytes());
        assertEquals(1, converter.get());
    }

    @Test
    @DisplayName("禁用缓存时每次调用转换器")
    void cacheDisabled() {
        var service = new OfficeToPdfService();
        var request = new OfficeToPdfRequest(new byte[]{1}, OfficeFormat.PPTX, OfficeEngine.NATIVE, 16, false);
        var converter = new java.util.concurrent.atomic.AtomicInteger(0);

        service.convert(request, r -> { converter.incrementAndGet(); return PDF; });
        service.convert(request, r -> { converter.incrementAndGet(); return PDF; });

        assertEquals(2, converter.get());
    }

    @Test
    @DisplayName("clearCache 清空缓存")
    void clearCache() {
        var service = new OfficeToPdfService();
        var request = OfficeToPdfRequest.of(new byte[]{1}, OfficeFormat.DOCX);

        service.convert(request, r -> PDF);
        service.clearCache();
        assertEquals(0, service.queueSize());
    }
}
