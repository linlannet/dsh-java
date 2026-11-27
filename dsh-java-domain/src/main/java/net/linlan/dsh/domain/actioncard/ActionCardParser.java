package net.linlan.dsh.domain.actioncard;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 从回答中的 action-card JSON 代码块提取独立、可操作的卡片。 */
public final class ActionCardParser {

    private static final Pattern ACTION_CARD_BLOCK = Pattern.compile(
            "^[\\t ]*```[\\t ]*action-card[\\t ]*\\R(.*?)^[\\t ]*```[\\t ]*$",
            Pattern.MULTILINE | Pattern.DOTALL);

    private final ObjectMapper objectMapper;

    public ActionCardParser() {
        this(new ObjectMapper());
    }

    public ActionCardParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
    }

    public List<ActionCard> parse(String answer) {
        if (answer == null || answer.isBlank()) {
            return List.of();
        }
        List<ActionCard> cards = new ArrayList<>();
        Matcher matcher = ACTION_CARD_BLOCK.matcher(answer);
        while (matcher.find()) {
            parseBlock(matcher.group(1)).ifPresent(cards::add);
        }
        return List.copyOf(cards);
    }

    private Optional<ActionCard> parseBlock(String body) {
        try {
            Map<String, Object> parsed = objectMapper.readValue(body,
                    new TypeReference<LinkedHashMap<String, Object>>() { });
            Map<String, Object> data = new LinkedHashMap<>(parsed);
            data.remove("callId");
            String id = stringValue(data.get("id"));
            if (id.isBlank()) {
                id = UUID.randomUUID().toString();
            }
            List<Action> actions = parseActions(data.get("actions"), id);
            return Optional.of(new ActionCard(
                    id,
                    stringValue(data.get("title")),
                    stringValue(data.get("description")),
                    actions,
                    immutableMap(data)
            ));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private static List<Action> parseActions(Object value, String cardId) {
        if (!(value instanceof List<?> values)) {
            return List.of();
        }
        List<Action> actions = new ArrayList<>();
        int index = 0;
        for (Object item : values) {
            if (!(item instanceof Map<?, ?> raw)) {
                continue;
            }
            Map<String, Object> data = new LinkedHashMap<>();
            raw.forEach((key, entry) -> data.put(String.valueOf(key), entry));
            String id = stringValue(data.get("id"));
            String operation = firstNonBlank(data, "action", "operation", "type", "name");
            if (id.isBlank()) {
                id = !operation.isBlank() ? operation : cardId + "-action-" + index;
            }
            if (operation.isBlank()) {
                operation = id;
            }
            String label = firstNonBlank(data, "label", "title");
            if (label.isBlank()) {
                label = id;
            }
            Object arguments = data.containsKey("arguments") ? data.get("arguments") : data.get("payload");
            Map<String, Object> argumentMap = arguments instanceof Map<?, ?> map
                    ? stringKeyedMap(map) : Map.of();
            actions.add(new Action(id, label, operation, immutableMap(argumentMap)));
            index++;
        }
        return List.copyOf(actions);
    }

    private static Map<String, Object> stringKeyedMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private static String firstNonBlank(Map<String, Object> data, String... keys) {
        for (String key : keys) {
            String value = stringValue(data.get(key));
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static Map<String, Object> immutableMap(Map<String, Object> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(key, immutableValue(value)));
        return Collections.unmodifiableMap(copy);
    }

    private static Object immutableValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return immutableMap(stringKeyedMap(map));
        }
        if (value instanceof List<?> list) {
            return list.stream().map(ActionCardParser::immutableValue).toList();
        }
        return value;
    }

    public record ActionCard(
            String id,
            String title,
            String description,
            List<Action> actions,
            Map<String, Object> data
    ) {
        public ActionCard {
            actions = actions == null ? List.of() : List.copyOf(actions);
            data = data == null ? Map.of() : immutableMap(data);
        }

        public boolean actionable() {
            return !actions.isEmpty();
        }

        public Optional<Action> action(String actionId) {
            return actions.stream().filter(action -> action.id().equals(actionId)).findFirst();
        }
    }

    public record Action(
            String id,
            String label,
            String operation,
            Map<String, Object> arguments
    ) {
        public Action {
            arguments = arguments == null ? Map.of() : immutableMap(arguments);
        }
    }
}
