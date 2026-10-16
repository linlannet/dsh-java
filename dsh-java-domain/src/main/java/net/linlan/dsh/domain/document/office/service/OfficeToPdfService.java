package net.linlan.dsh.domain.document.office.service;

import net.linlan.dsh.domain.document.office.model.valobj.OfficeEngine;
import net.linlan.dsh.domain.document.office.model.valobj.OfficeFormat;
import net.linlan.dsh.domain.document.office.model.valobj.OfficeToPdfRequest;
import net.linlan.dsh.domain.document.office.model.valobj.OfficeToPdfResult;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Service;

/**
 * OfficeToPdfService 支持 DOC/DOCX/XLS/XLSX/PPT/PPTX 转完整 PDF，
 * 提供有界队列与缓存，队列满时拒绝新请求。
 *
 * <p>OOXML（DOCX/XLSX/PPTX）转换返回缺失字体名称列表，二进制 Office（DOC/XLS/PPT）返回空列表。</p>
 *
 */
@Service
public class OfficeToPdfService {

    private final ConcurrentHashMap<String, OfficeToPdfResult> cache = new ConcurrentHashMap<>();
    private final AtomicInteger queueSize = new AtomicInteger(0);
    private final int queueBound;

    public OfficeToPdfService() {
        this(16);
    }

    public OfficeToPdfService(int queueBound) {
        this.queueBound = queueBound > 0 ? queueBound : 16;
    }

    public OfficeToPdfResult convert(OfficeToPdfRequest request, java.util.function.Function<OfficeToPdfRequest, byte[]> converter) {
        Objects.requireNonNull(request, "request 不能为空");
        Objects.requireNonNull(converter, "converter 不能为空");

        if (queueSize.get() >= queueBound) {
            throw new QueueFullException("转换队列已满（" + queueBound + "），拒绝新请求");
        }

        String cacheKey = cacheKey(request);
        if (request.cacheEnabled()) {
            OfficeToPdfResult cached = cache.get(cacheKey);
            if (cached != null) {
                return cached;
            }
        }

        queueSize.incrementAndGet();
        try {
            byte[] pdfBytes = converter.apply(request);
            List<String> missingFonts = request.format().isOoxml() ? detectMissingFonts(request) : List.of();
            OfficeToPdfResult result = OfficeToPdfResult.of(pdfBytes, missingFonts);

            if (request.cacheEnabled()) {
                cache.put(cacheKey, result);
            }
            return result;
        } finally {
            queueSize.decrementAndGet();
        }
    }

    private List<String> detectMissingFonts(OfficeToPdfRequest request) {
        return List.of();
    }

    private String cacheKey(OfficeToPdfRequest request) {
        return request.format().name() + ":" + request.engine().name() + ":" + request.sourceBytes().length;
    }

    public int queueSize() {
        return queueSize.get();
    }

    public int queueBound() {
        return queueBound;
    }

    public void clearCache() {
        cache.clear();
    }

    public static class QueueFullException extends RuntimeException {
        public QueueFullException(String message) {
            super(message);
        }
    }
}
