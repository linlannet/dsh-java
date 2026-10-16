package net.linlan.dsh.domain.speech.service;

import net.linlan.dsh.domain.speech.adapter.port.ISpeechProviderPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SpeechToTextService 语音转文本")
class SpeechToTextServiceTest {
    private static class StubProvider implements ISpeechProviderPort {
        final String name; final boolean avail; final String result;
        StubProvider(String name, boolean avail, String result) { this.name = name; this.avail = avail; this.result = result; }
        @Override public String name() { return name; }
        @Override public boolean available() { return avail; }
        @Override public Optional<String> transcribe(byte[] audioData) { return Optional.ofNullable(result); }
    }

    @Test
    @DisplayName("discoverProviders 返回可用 Provider")
    void discover() {
        var service = new SpeechToTextService(List.of(
            new StubProvider("sensevoice", true, "text"),
            new StubProvider("whisper", false, null)
        ));
        assertEquals(List.of("sensevoice"), service.discoverProviders());
    }

    @Test
    @DisplayName("transcribe 返回第一个可用 Provider 结果")
    void transcribe() {
        var service = new SpeechToTextService(List.of(new StubProvider("p1", true, "hello")));
        assertEquals(Optional.of("hello"), service.transcribe(new byte[]{1}));
    }

    @Test
    @DisplayName("无可用 Provider 返回空")
    void noProvider() {
        var service = new SpeechToTextService(List.of());
        assertTrue(service.transcribe(new byte[]{1}).isEmpty());
    }

    @Test
    @DisplayName("transcribeBounded 截断音频")
    void transcribeBounded() {
        var service = new SpeechToTextService(List.of(new StubProvider("p1", true, "bounded")));
        assertTrue(service.transcribeBounded(new byte[]{1, 2, 3, 4, 5}, 3).isPresent());
    }
}
