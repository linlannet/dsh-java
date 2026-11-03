package net.linlan.dsh.domain.bot;

import net.linlan.dsh.domain.bot.model.aggregate.FeishuBotAggregate;
import net.linlan.dsh.domain.bot.model.valobj.FeishuConnectionState;
import net.linlan.dsh.domain.bot.model.valobj.FeishuInboundMessage;
import net.linlan.dsh.domain.bot.model.valobj.FeishuPairingBeginVO;
import net.linlan.dsh.domain.bot.model.valobj.FeishuPairingPollVO;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 飞书领域模型单元测试：聚合派生/判定/状态迁移与值对象行为。
 */
class FeishuBotAggregateTest {

    private FeishuBotAggregate newBot(boolean enabled, boolean groupRequireMention) {
        return new FeishuBotAggregate("fbot-abc", "Bot", "cli_xxx", "FEISHU_BOT_FBOT_ABC_APP_SECRET", enabled,
                "feishu-agent", null, null, groupRequireMention, Instant.now(), Instant.now());
    }

    @Test
    void deriveAgentIdKeepsLast16OfChatId() {
        FeishuBotAggregate bot = newBot(false, true);
        String chatId = "oc_0123456789abcdefXYZW";
        String agentId = bot.deriveAgentId(chatId);
        assertEquals("feishu-agent-fs-" + chatId.substring(chatId.length() - 16), agentId);
    }

    @Test
    void deriveAgentIdFallsBackToAnonWhenChatIdMissing() {
        FeishuBotAggregate bot = newBot(false, true);
        assertEquals("feishu-agent-fs-anon", bot.deriveAgentId(null));
    }

    @Test
    void shouldHandleP2PAlways() {
        FeishuBotAggregate bot = newBot(false, true);
        FeishuInboundMessage p2p = new FeishuInboundMessage("fbot-abc", "m1", "c1", "p2p", "text", "hi", null, null);
        assertTrue(bot.shouldHandle(p2p));
    }

    @Test
    void shouldHandleGroupRespectsMentionFlag() {
        FeishuInboundMessage group = new FeishuInboundMessage("fbot-abc", "m1", "c1", "group", "text", "hi", null, null);
        assertFalse(newBot(false, true).shouldHandle(group));
        assertTrue(newBot(false, false).shouldHandle(group));
    }

    @Test
    void stateTransitionsFollowLifecycle() {
        FeishuBotAggregate bot = newBot(false, true);
        assertEquals(FeishuConnectionState.IDLE, bot.getConnectionState());
        bot.onConnecting();
        assertEquals(FeishuConnectionState.CONNECTING, bot.getConnectionState());
        bot.onConnected();
        assertEquals(FeishuConnectionState.CONNECTED, bot.getConnectionState());
        assertTrue(bot.getConnectionState().isOnline());
        bot.onError("boom");
        assertEquals(FeishuConnectionState.ERROR, bot.getConnectionState());
        assertEquals("boom", bot.getLastError());
        bot.onStopped();
        assertEquals(FeishuConnectionState.IDLE, bot.getConnectionState());
        assertNull(bot.getLastError());
    }

    @Test
    void enabledBotStartsAsConnecting() {
        assertEquals(FeishuConnectionState.CONNECTING, newBot(true, true).getConnectionState());
    }

    @Test
    void constructorRejectsBlankKeyFields() {
        assertThrows(IllegalArgumentException.class,
                () -> new FeishuBotAggregate("", "n", "app", "ref", false, "base", null, null, true, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new FeishuBotAggregate("id", "n", " ", "ref", false, "base", null, null, true, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> new FeishuBotAggregate("id", "n", "app", "ref", false, " ", null, null, true, null, null));
    }

    @Test
    void renameAndUpdateConfigApplyTrimSemantics() {
        FeishuBotAggregate bot = newBot(false, true);
        bot.rename("New Name");
        assertEquals("New Name", bot.getBotName());
        bot.rename("  ");
        assertEquals("New Name", bot.getBotName());
        bot.updateConfig("  base2  ", "  ch  ", "  /tmp  ");
        assertEquals("base2", bot.getAgentBaseId());
        assertEquals("ch", bot.getChannelCode());
        assertEquals("/tmp", bot.getCwd());
        bot.updateConfig(null, "  ", null);
        assertNull(bot.getChannelCode());
    }

    @Test
    void pairingValueObjectsBehave() {
        FeishuPairingBeginVO begin = new FeishuPairingBeginVO("url", "uc", "dc", 3600L, 5);
        assertEquals(5, begin.intervalSec());
        assertEquals(3600L, begin.expireInSec());

        assertTrue(new FeishuPairingPollVO("cli", "secret", "open", "feishu", null, null).succeeded());
        assertFalse(new FeishuPairingPollVO(null, null, null, null, "authorization_pending", null).succeeded());
        assertFalse(new FeishuPairingPollVO("", "secret", null, null, null, null).succeeded());
    }

    @Test
    void inboundMessageTextPredicates() {
        assertTrue(new FeishuInboundMessage("b", "m", "c", "p2p", "text", "hi", null, null).hasText());
        assertFalse(new FeishuInboundMessage("b", "m", "c", "p2p", "text", "  ", null, null).hasText());
        assertFalse(new FeishuInboundMessage("b", "m", "c", "group", "text", "hi", null, null).isP2P());
    }
}