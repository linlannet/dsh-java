package net.linlan.dsh.domain.runtime.profile.preset;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class YamlPresetLoader {

    public PresetDefinition load(String yamlContent) {
        Objects.requireNonNull(yamlContent, "yamlContent");
        String name = "";
        String description = "";
        String systemPrompt = "";
        Map<String, String> attributes = new LinkedHashMap<>();

        for (String rawLine : yamlContent.split("\n", -1)) {
            String line = stripComment(rawLine).trim();
            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith("- ")) {
                continue;
            }
            int colon = indexOfTopLevelColon(line);
            if (colon <= 0) {
                continue;
            }
            String key = line.substring(0, colon).trim();
            String value = line.substring(colon + 1).trim();
            if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
                value = value.substring(1, value.length() - 1);
            } else if (value.startsWith("'") && value.endsWith("'") && value.length() >= 2) {
                value = value.substring(1, value.length() - 1);
            }
            switch (key) {
                case "name" -> name = value;
                case "description" -> description = value;
                case "system_prompt" -> systemPrompt = value;
                default -> attributes.put(key, value);
            }
        }
        return new PresetDefinition(name, description, systemPrompt, attributes);
    }

    public PresetDefinition loadFromFile(String path) {
        Objects.requireNonNull(path, "path");
        try {
            String content = Files.readString(Path.of(path), StandardCharsets.UTF_8);
            return load(content);
        } catch (IOException e) {
            throw new IllegalArgumentException("failed to read preset file: " + path, e);
        }
    }

    private String stripComment(String line) {
        boolean inQuote = false;
        char quote = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuote) {
                if (c == quote) {
                    inQuote = false;
                }
            } else {
                if (c == '"' || c == '\'') {
                    inQuote = true;
                    quote = c;
                } else if (c == '#') {
                    return line.substring(0, i);
                }
            }
        }
        return line;
    }

    private int indexOfTopLevelColon(String line) {
        boolean inQuote = false;
        char quote = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuote) {
                if (c == quote) {
                    inQuote = false;
                }
            } else {
                if (c == '"' || c == '\'') {
                    inQuote = true;
                    quote = c;
                } else if (c == ':') {
                    return i;
                }
            }
        }
        return -1;
    }

    public record PresetDefinition(
            String name,
            String description,
            String systemPrompt,
            Map<String, String> attributes
    ) {
        public PresetDefinition {
            attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        }

        public PersonaDefinition toPersona() {
            return new PersonaDefinition(name, description, systemPrompt, attributes);
        }
    }
}
