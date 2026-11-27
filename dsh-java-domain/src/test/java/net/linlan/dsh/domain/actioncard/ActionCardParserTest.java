package net.linlan.dsh.domain.actioncard;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Action 卡片代码块解析")
class ActionCardParserTest {

    private final ActionCardParser parser = new ActionCardParser();

    @Test
    @DisplayName("解析回答中的 action-card JSON 块并保留可操作动作")
    void parsesActionableCard() {
        String answer = """
                可以执行以下操作：
                ```action-card
                {
                  "id": "deploy-card",
                  "callId": "legacy-call",
                  "title": "部署确认",
                  "description": "选择部署环境",
                  "actions": [
                    {"id": "staging", "label": "预发", "action": "deploy",
                     "arguments": {"environment": "staging"}},
                    {"id": "cancel", "label": "取消", "type": "dismiss"}
                  ],
                  "severity": "info"
                }
                ```
                后续正文
                """;

        List<ActionCardParser.ActionCard> cards = parser.parse(answer);

        assertEquals(1, cards.size());
        ActionCardParser.ActionCard card = cards.get(0);
        assertEquals("deploy-card", card.id());
        assertEquals("部署确认", card.title());
        assertTrue(card.actionable());
        assertEquals("deploy", card.action("staging").orElseThrow().operation());
        assertEquals(Map.of("environment", "staging"),
                card.action("staging").orElseThrow().arguments());
        assertEquals("info", card.data().get("severity"));
        assertFalse(card.data().containsKey("callId"));
    }

    @Test
    @DisplayName("连续轮次的卡片独立保留")
    void cardsRemainIndependentAcrossTurns() {
        var first = parser.parse(card("first", "old-action")).get(0);

        var second = parser.parse(card("second", "new-action")).get(0);

        assertNotEquals(first.id(), second.id());
        assertTrue(first.action("old-action").isPresent());
        assertTrue(second.action("new-action").isPresent());
        assertTrue(second.action("old-action").isEmpty());
        assertTrue(first.actionable());
        assertTrue(second.actionable());
    }

    @Test
    @DisplayName("忽略其他代码块和无效卡片 JSON")
    void ignoresOtherAndMalformedBlocks() {
        String answer = """
                ```json
                {"actions": []}
                ```
                ```action-card
                not-json
                ```
                """;

        assertTrue(parser.parse(answer).isEmpty());
        assertTrue(parser.parse(null).isEmpty());
    }

    private static String card(String id, String action) {
        return """
                ```action-card
                {"id":"%s","title":"card","actions":[{"id":"%s","label":"run"}]}
                ```
                """.formatted(id, action);
    }
}
