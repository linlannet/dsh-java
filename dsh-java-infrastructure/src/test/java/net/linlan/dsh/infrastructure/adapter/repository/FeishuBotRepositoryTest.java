package net.linlan.dsh.infrastructure.adapter.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.linlan.dsh.domain.bot.model.aggregate.FeishuBotAggregate;
import net.linlan.dsh.domain.bot.model.valobj.FeishuConnectionState;
import net.linlan.dsh.infrastructure.dao.IFeishuBotDao;
import net.linlan.dsh.infrastructure.dao.po.FeishuBotPO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 飞书 Bot 仓储适配器测试：校验 PO 与聚合的双向映射（0/1 ↔ boolean、运行态不落库）。
 */
class FeishuBotRepositoryTest {

    private FakeDao dao;
    private FeishuBotRepository repository;

    @BeforeEach
    void setUp() {
        this.dao = new FakeDao();
        this.repository = new FeishuBotRepository(this.dao);
    }

    @Test
    void saveMapsBooleansToIntegers() {
        FeishuBotAggregate bot = new FeishuBotAggregate("fbot-1", "Bot", "cli_1", "REF_1", true,
                "feishu-agent", null, "/tmp", false, Instant.now(), Instant.now());

        this.repository.save(bot);

        FeishuBotPO po = this.dao.store.get("fbot-1");
        assertNotNull(po);
        assertEquals(1, po.enabled());
        assertEquals(0, po.groupRequireMention());
        assertEquals("REF_1", po.secretRef());
        assertNotNull(po.updatedAt(), "保存应刷新 updatedAt");
    }

    @Test
    void findByBotIdMapsIntegersBackToBooleans() {
        this.dao.save(new FeishuBotPO("fbot-2", "Bot2", "cli_2", "REF_2", 0, "feishu-agent",
                null, "/tmp", 1, Instant.now(), Instant.now()));

        Optional<FeishuBotAggregate> found = this.repository.findByBotId("fbot-2");

        assertTrue(found.isPresent());
        FeishuBotAggregate bot = found.get();
        assertFalse(bot.isEnabled());
        assertTrue(bot.isGroupRequireMention());
        assertEquals(FeishuConnectionState.IDLE, bot.getConnectionState(), "未启用应回落到 IDLE");
    }

    @Test
    void enabledBotStartsInConnectingState() {
        this.dao.save(new FeishuBotPO("fbot-3", "Bot3", "cli_3", "REF_3", 1, "feishu-agent",
                null, "/tmp", 0, Instant.now(), Instant.now()));

        FeishuBotAggregate bot = this.repository.findByBotId("fbot-3").orElseThrow();

        assertTrue(bot.isEnabled());
        assertEquals(FeishuConnectionState.CONNECTING, bot.getConnectionState());
    }

    @Test
    void runtimeStateIsNeverPersisted() {
        FeishuBotAggregate bot = new FeishuBotAggregate("fbot-4", "Bot4", "cli_4", "REF_4", true,
                "feishu-agent", null, "/tmp", true, Instant.now(), Instant.now());
        bot.onError("连接失败");
        assertEquals(FeishuConnectionState.ERROR, bot.getConnectionState());

        this.repository.save(bot);
        FeishuBotAggregate reloaded = this.repository.findByBotId("fbot-4").orElseThrow();

        assertEquals(FeishuConnectionState.CONNECTING, reloaded.getConnectionState(), "运行态不应落库");
        assertNull(reloaded.getLastError(), "运行态错误不应落库");
        assertEquals("连接失败", bot.getLastError(), "内存态仍保留错误供排障");
    }

    @Test
    void findAllAndDeleteDelegateToDao() {
        this.dao.save(new FeishuBotPO("fbot-5", "Bot5", "cli_5", "REF_5", 1, "feishu-agent",
                null, "/tmp", 1, Instant.now(), Instant.now()));
        this.dao.save(new FeishuBotPO("fbot-6", "Bot6", "cli_6", "REF_6", 0, "feishu-agent",
                null, "/tmp", 1, Instant.now(), Instant.now()));

        List<FeishuBotAggregate> all = this.repository.findAll();
        assertEquals(2, all.size());
        assertEquals("Bot5", all.get(0).getBotName());

        this.repository.deleteByBotId("fbot-5");
        assertTrue(this.repository.findByBotId("fbot-5").isEmpty());
        assertEquals(1, this.repository.findAll().size());
    }

    /**
     * 内存 DAO 桩。
     */
    private static final class FakeDao implements IFeishuBotDao {

        private final Map<String, FeishuBotPO> store = new LinkedHashMap<>();

        @Override
        public void save(FeishuBotPO po) {
            this.store.put(po.botId(), po);
        }

        @Override
        public Optional<FeishuBotPO> findByBotId(String botId) {
            return Optional.ofNullable(this.store.get(botId));
        }

        @Override
        public List<FeishuBotPO> findAll() {
            return new ArrayList<>(this.store.values());
        }

        @Override
        public void deleteByBotId(String botId) {
            this.store.remove(botId);
        }
    }
}
