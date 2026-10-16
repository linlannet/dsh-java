package net.linlan.dsh.domain.translation.cot.service;

import net.linlan.dsh.domain.translation.cot.adapter.port.ITranslatorPort;
import java.util.Objects;
import java.util.Optional;

/**
 * CotTranslationService 将展开的思考内容翻译为界面语言，原文保持不变。
 * 实验性扩展，默认关闭。
 */
public class CotTranslationService {

    private final ITranslatorPort translator;

    public CotTranslationService(ITranslatorPort translator) {
        this.translator = Objects.requireNonNull(translator);
    }

    public TranslationResult translate(String original, String targetLang) {
        Objects.requireNonNull(original, "original 不能为空");
        try {
            Optional<String> translated = translator.translate(original, targetLang);
            return new TranslationResult(original, translated.orElse(original));
        } catch (Exception e) {
            return new TranslationResult(original, original);
        }
    }

    public record TranslationResult(String original, String translated) {
        public String original() { return original; }
    }
}
