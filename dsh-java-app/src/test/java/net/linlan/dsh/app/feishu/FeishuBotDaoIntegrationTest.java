package net.linlan.dsh.app.feishu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import net.linlan.dsh.app.Application;
import net.linlan.dsh.domain.bot.adapter.port.IFeishuBotConnectionPort;
import net.linlan.dsh.domain.bot.adapter.port.IFeishuMessagingPort;
import net.linlan.dsh.domain.bot.adapter.repository.IFeishuBotRepository;
import net.linlan.dsh.infrastructure.dao.IFeishuBotDao;
import net.linlan.dsh.infrastructure.dao.po.FeishuBotPO;
import net.linlan.dsh.trigger.http.BotController;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * 飞书 Bot 持久化与装配集成测试：H2 {@code MODE=MySQL} 下 DAO CRUD，以及总开关开/关的上下文冒烟。
 *
 * <p>对应 tasks 11.4 的 ②（DAO CRUD）与 ③（上下文冒烟）。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FeishuBotDaoIntegrationTest {

    private ConfigurableApplicationContext context;
    private IFeishuBotDao dao;

    @BeforeAll
    void startApplication() throws Exception {
        Path temp = Files.createTempDirectory("dsh-feishu-it");
        this.context = new SpringApplicationBuilder(Application.class)
                .run(
                        "--server.port=0",
                        "--spring.profiles.active=standalone",
                        "--spring.sql.init.mode=always",
                        "--spring.datasource.url=jdbc:h2:mem:feishu_it;MODE=MySQL;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
                        "--harness.workspace.root=" + temp,
                        "--harness.storage.root=" + temp.resolve("storage"),
                        "--harness.agent.default-cwd=" + temp,
                        "--harness.terminal.default-cwd=" + temp
                );
        this.dao = this.context.getBean(IFeishuBotDao.class);
    }

    @AfterAll
    void stopApplication() {
        if (this.context != null) {
            this.context.close();
        }
    }

    @Test
    void daoSupportsSaveFindAndDelete() {
        FeishuBotPO po = new FeishuBotPO("fbot-it-1", "集成 Bot", "cli_it", "FEISHU_BOT_FBOT_IT_1_APP_SECRET",
                1, "feishu-agent", null, "/tmp", 1, Instant.now(), Instant.now());

        this.dao.save(po);
        Optional<FeishuBotPO> found = this.dao.findByBotId("fbot-it-1");
        assertTrue(found.isPresent());
        assertEquals("集成 Bot", found.get().botName());
        assertEquals(1, found.get().enabled());

        this.dao.save(new FeishuBotPO("fbot-it-1", "改名后", "cli_it", "FEISHU_BOT_FBOT_IT_1_APP_SECRET",
                0, "feishu-agent", null, "/tmp", 0, Instant.now(), Instant.now()));
        FeishuBotPO updated = this.dao.findByBotId("fbot-it-1").orElseThrow();
        assertEquals("改名后", updated.botName(), "save 应为 upsert");
        assertEquals(0, updated.enabled());

        List<FeishuBotPO> all = this.dao.findAll();
        assertTrue(all.stream().anyMatch(item -> "fbot-it-1".equals(item.botId())));

        this.dao.deleteByBotId("fbot-it-1");
        assertTrue(this.dao.findByBotId("fbot-it-1").isEmpty());
    }

    @Test
    void enabledByDefaultAssemblesFeishuBeans() {
        assertTrue(this.context.getBeanNamesForType(IFeishuMessagingPort.class).length > 0, "开启时应装配出站消息端口");
        assertTrue(this.context.getBeanNamesForType(IFeishuBotRepository.class).length > 0);
        assertTrue(this.context.getBeanNamesForType(IFeishuBotConnectionPort.class).length > 0);
    }

    @Test
    void disabledSwitchKeepsContextHealthyWithoutFeishuBeans() throws Exception {
        Path temp = Files.createTempDirectory("dsh-feishu-off");
        try (ConfigurableApplicationContext off = new SpringApplicationBuilder(Application.class)
                .run(
                        "--server.port=0",
                        "--spring.profiles.active=standalone",
                        "--spring.sql.init.mode=always",
                        "--dsh.feishu.enabled=false",
                        "--spring.datasource.url=jdbc:h2:mem:feishu_off;MODE=MySQL;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
                        "--harness.workspace.root=" + temp,
                        "--harness.storage.root=" + temp.resolve("storage"),
                        "--harness.agent.default-cwd=" + temp,
                        "--harness.terminal.default-cwd=" + temp
                )) {
            assertTrue(off.isActive(), "关闭飞书时上下文应正常启动");
            assertEquals(0, off.getBeanNamesForType(IFeishuMessagingPort.class).length, "关闭时不应产出飞书 Bean");
            assertEquals(0, off.getBeanNamesForType(IFeishuBotConnectionPort.class).length);
            assertEquals(0, off.getBeanNamesForType(BotController.class).length, "关闭时不应注册飞书控制器");
        }
    }
}
