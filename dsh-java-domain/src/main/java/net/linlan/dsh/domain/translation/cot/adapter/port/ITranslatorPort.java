package net.linlan.dsh.domain.translation.cot.adapter.port;

import java.util.Optional;

public interface ITranslatorPort {
    Optional<String> translate(String text, String targetLang);
}
