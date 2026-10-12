package net.linlan.dsh.domain.runtime.profile.preset;

import java.util.Map;

public record PersonaDefinition(
        String name,
        String description,
        String systemPrompt,
        Map<String, String> attributes
) {
    public PersonaDefinition {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("persona name required");
        }
        description = description == null ? "" : description;
        systemPrompt = systemPrompt == null ? "" : systemPrompt;
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
