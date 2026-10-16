package net.linlan.dsh.domain.speech.adapter.port;

import java.util.Optional;

public interface ISpeechProviderPort {
    String name();
    boolean available();
    Optional<String> transcribe(byte[] audioData);
}
