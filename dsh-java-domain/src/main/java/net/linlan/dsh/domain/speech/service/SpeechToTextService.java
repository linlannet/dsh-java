package net.linlan.dsh.domain.speech.service;

import net.linlan.dsh.domain.speech.adapter.port.ISpeechProviderPort;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * SpeechToTextService 提供 Provider 发现、完整录音转写、有界临时转写。
 * 实验性扩展，默认关闭。
 */
public class SpeechToTextService {

    private final List<ISpeechProviderPort> providers;

    public SpeechToTextService(List<ISpeechProviderPort> providers) {
        this.providers = Objects.requireNonNull(providers);
    }

    public List<String> discoverProviders() {
        return providers.stream().filter(ISpeechProviderPort::available).map(ISpeechProviderPort::name).toList();
    }

    public Optional<String> transcribe(byte[] audioData) {
        for (var p : providers) {
            if (p.available()) {
                var result = p.transcribe(audioData);
                if (result.isPresent()) return result;
            }
        }
        return Optional.empty();
    }

    public Optional<String> transcribeBounded(byte[] audioData, int maxBytes) {
        byte[] bounded = audioData.length > maxBytes ? java.util.Arrays.copyOf(audioData, maxBytes) : audioData;
        return transcribe(bounded);
    }
}
