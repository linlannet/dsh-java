package net.linlan.dsh.domain.bot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.linlan.dsh.domain.bot.model.valobj.FeishuConnectionState;
import net.linlan.dsh.domain.bot.model.valobj.FeishuInboundMessage;
import net.linlan.dsh.domain.bot.model.valobj.FeishuPairingBeginVO;
import net.linlan.dsh.domain.bot.model.valobj.FeishuPairingPollVO;
import org.junit.jupiter.api.Test;

/**
 * 飞书值对象测试：连接状态判定、入站消息判定、配对 VO 成功判定。
 */
class FeishuValueObjectTest {

    @Test
    void connectionStateOnlineOnlyWhenConnected() {
        assertTrue(FeishuConnectionState.CONNECTED.isOnline());
        assertFalse(FeishuConnectionState.IDLE.isOnline());
        assertFalse(FeishuConnectionState.CONNECTING.isOnline());
        assertFalse(FeishuConnectionState.ERROR.isOnline());
        assertEquals("IDLE", FeishuConnectionState.IDLE.name());
    }

    @Test
    void inboundMessageDistinguishesP2pAndText() {
        FeishuInboundMessage p2p = new FeishuInboundMessage("fbot-1", "m1", "oc_1", "p2p", "text", "你好", "ou_1", "用户1");
        assertTrue(p2p.isP2P());
        assertTrue(p2p.hasText());

        FeishuInboundMessage group = new FeishuInboundMessage("fbot-1", "m2", "oc_2", "group", "text", " ", "ou_2", "用户2");
        assertFalse(group.isP2P());
        assertFalse(group.hasText(), "空白文本视为无文本");

        FeishuInboundMessage image = new FeishuInboundMessage("fbot-1", "m3", "oc_1", "p2p", "image", null, "ou_1", null);
        assertTrue(image.isP2P());
        assertFalse(image.hasText());
        assertNull(image.text());
    }

    @Test
    void pairingPollSucceedsOnlyWithCredentials() {
        FeishuPairingPollVO ok = new FeishuPairingPollVO("cli_1", "sec_1", "ou_1", "feishu", null, null);
        assertTrue(ok.succeeded());

        FeishuPairingPollVO missingSecret = new FeishuPairingPollVO("cli_1", null, "ou_1", "feishu", null, null);
        assertFalse(missingSecret.succeeded());

        FeishuPairingPollVO pending = new FeishuPairingPollVO(null, null, null, null, "authorization_pending", "waiting");
        assertFalse(pending.succeeded());
        assertEquals("authorization_pending", pending.error());
    }

    @Test
    void pairingBeginKeepsIntervalIntAndExpireLong() {
        FeishuPairingBeginVO vo = new FeishuPairingBeginVO("https://accounts.feishu.cn/sign", "ABCD", "dv_1", 1800L, 5);

        assertEquals("https://accounts.feishu.cn/sign", vo.url());
        assertEquals("ABCD", vo.userCode());
        assertEquals("dv_1", vo.deviceCode());
        assertEquals(1800L, vo.expireInSec());
        assertEquals(5, vo.intervalSec());
    }
}
