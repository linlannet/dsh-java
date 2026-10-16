package net.linlan.dsh.domain.translation.cot.service;

import net.linlan.dsh.domain.translation.cot.adapter.port.ITranslatorPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CotTranslationService 思维链翻译")
class CotTranslationServiceTest {
    @Test
    @DisplayName("翻译成功返回译文，原文不变")
    void translateSuccess() {
        var service = new CotTranslationService((text, lang) -> Optional.of("翻译结果"));
        var result = service.translate("original text", "zh");
        assertEquals("original text", result.original());
        assertEquals("翻译结果", result.translated());
    }

    @Test
    @DisplayName("翻译不可用时返回原文")
    void translateUnavailable() {
        var service = new CotTranslationService((text, lang) -> Optional.empty());
        var result = service.translate("original", "zh");
        assertEquals("original", result.translated());
    }

    @Test
    @DisplayName("翻译异常时返回原文")
    void translateException() {
        var service = new CotTranslationService((text, lang) -> { throw new RuntimeException("不可用"); });
        var result = service.translate("original", "zh");
        assertEquals("original", result.translated());
    }
}
