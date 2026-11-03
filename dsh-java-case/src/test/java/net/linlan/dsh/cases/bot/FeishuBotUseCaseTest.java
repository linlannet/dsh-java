package net.linlan.dsh.cases.bot;

import net.linlan.dsh.api.dto.FeishuBotDTO;
import net.linlan.dsh.api.dto.FeishuBotSaveDTO;
import net.linlan.dsh.api.response.Response;
import net.linlan.dsh.domain.bot.adapter.port.IFeishuBotConnectionPort;
import net.linlan.dsh.domain.bot.adapter.repository.IFeishuBotRepository;
import net.linlan.dsh.domain.bot.model.aggregate.FeishuBotAggregate;
import net.linlan.dsh.domain.credentials.adapter.port.ICredentialProviderPort;
import net.linlan.dsh.domain.credentials.model.valobj.CredentialInfoVO;
import net.linlan.dsh.domain.credentials.model.valobj.CredentialRef;
import net.linlan.dsh.domain.credentials.model.valobj.ResolvedCredentialVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 飞书 Bot 生命周期用例单元测试（使用内存假实现）。
 */
class FeishuBotUseCaseTest {

    private FeishuBotUseCase useCase;
    private InMemoryRepo repo;
    private FakeConn conn;
    private InMemoryCred cred;

    @BeforeEach
    void setUp() throws Exception {
        useCase = new FeishuBotUseCase();
        repo = new InMemoryRepo();
        conn = new FakeConn();
        cred = new InMemoryCred();
        inject(useCase, "botRepository", repo);
        inject(useCase, "connectionPort", conn);
        inject(useCase, "credentialPort", cred);
    }

    @Test
    void saveNewBotWithoutAppIdFails() {
        Response<FeishuBotDTO> resp = useCase.saveBot(new FeishuBotSaveDTO(null, "Bot", null, null, "base", null, null, null));
        assertEquals(Response.INVALID_ARGUMENT_CODE, resp.code());
        assertTrue(resp.info().contains("必须提供 appId"));
    }

    @Test
    void saveNewBotWithInvalidBotIdFails() {
        Response<FeishuBotDTO> resp = useCase.saveBot(new FeishuBotSaveDTO("_bad", "Bot", "cli", "s", "base", null, null, null));
        assertEquals(Response.INVALID_ARGUMENT_CODE, resp.code());
    }

    @Test
    void saveNewBotDerivesSecretRefAndReportsConfigured() {
        Response<FeishuBotDTO> resp = useCase.saveBot(
                new FeishuBotSaveDTO("fbot-abc", "Bot", "cli_x", "secret-val", "base", null, null, null));
        assertEquals(Response.SUCCESS_CODE, resp.code());
        assertEquals("FEISHU_BOT_FBOT_ABC_APP_SECRET", resp.data().secretRef());
        assertTrue(resp.data().secretConfigured());
        assertEquals("cli_x", resp.data().appId());
    }

    @Test
    void updateBotForcesPathBotId() {
        useCase.saveBot(new FeishuBotSaveDTO("fbot-abc", "Bot", "cli_x", "s", "base", null, null, null));
        Response<FeishuBotDTO> resp = useCase.updateBot("fbot-abc",
                new FeishuBotSaveDTO("ignored", "Renamed", null, null, "base2", null, null, null));
        assertEquals(Response.SUCCESS_CODE, resp.code());
        assertEquals("fbot-abc", resp.data().botId());
        assertEquals("Renamed", resp.data().botName());
    }

    @Test
    void enableBotWithoutSecretFails() {
        repo.map.put("fbot-abc", new FeishuBotAggregate("fbot-abc", "Bot", "cli", "REF_X", false,
                "base", null, null, true, null, null));
        Response<FeishuBotDTO> resp = useCase.enableBot("fbot-abc");
        assertEquals(Response.INVALID_ARGUMENT_CODE, resp.code());
        assertTrue(resp.info().contains("app_secret"));
    }

    @Test
    void enableBotWithSecretStartsConnection() {
        useCase.saveBot(new FeishuBotSaveDTO("fbot-abc", "Bot", "cli", "s", "base", null, null, null));
        Response<FeishuBotDTO> resp = useCase.enableBot("fbot-abc");
        assertEquals(Response.SUCCESS_CODE, resp.code());
        assertTrue(resp.data().enabled());
        assertTrue(conn.started.contains("fbot-abc"));
    }

    @Test
    void deleteBotStopsConnectionAndClearsSecret() {
        useCase.saveBot(new FeishuBotSaveDTO("fbot-abc", "Bot", "cli", "s", "base", null, null, null));
        Response<Void> resp = useCase.deleteBot("fbot-abc");
        assertEquals(Response.SUCCESS_CODE, resp.code());
        assertFalse(repo.map.containsKey("fbot-abc"));
        assertTrue(conn.stopped.contains("fbot-abc"));
        assertFalse(cred.store.containsKey("FEISHU_BOT_FBOT_ABC_APP_SECRET"));
    }

    @Test
    void listBotsReturnsAll() {
        useCase.saveBot(new FeishuBotSaveDTO("fbot-a", "A", "cliA", "s", "baseA", null, null, null));
        useCase.saveBot(new FeishuBotSaveDTO("fbot-b", "B", "cliB", "s", "baseB", null, null, null));
        assertEquals(2, useCase.listBots().data().size());
    }

    private static void inject(Object target, String field, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, value);
    }

    static class InMemoryRepo implements IFeishuBotRepository {
        final Map<String, FeishuBotAggregate> map = new LinkedHashMap<>();

        @Override
        public void save(FeishuBotAggregate bot) {
            map.put(bot.getBotId(), bot);
        }

        @Override
        public Optional<FeishuBotAggregate> findByBotId(String botId) {
            return Optional.ofNullable(map.get(botId));
        }

        @Override
        public List<FeishuBotAggregate> findAll() {
            return new ArrayList<>(map.values());
        }

        @Override
        public void deleteByBotId(String botId) {
            map.remove(botId);
        }
    }

    static class FakeConn implements IFeishuBotConnectionPort {
        final List<String> started = new ArrayList<>();
        final List<String> stopped = new ArrayList<>();

        @Override
        public void start(FeishuBotAggregate bot) {
            started.add(bot.getBotId());
        }

        @Override
        public void stop(String botId) {
            stopped.add(botId);
        }

        @Override
        public boolean isConnected(String botId) {
            return false;
        }

        @Override
        public String stateOf(String botId) {
            return "IDLE";
        }

        @Override
        public String lastErrorOf(String botId) {
            return null;
        }
    }

    static class InMemoryCred implements ICredentialProviderPort {
        final Map<String, String> store = new LinkedHashMap<>();

        @Override
        public Optional<ResolvedCredentialVO> resolve(CredentialRef ref) {
            String v = store.get(ref.value());
            return v == null ? Optional.empty() : Optional.of(new ResolvedCredentialVO(v, "file"));
        }

        @Override
        public CredentialInfoVO describe(CredentialRef ref) {
            return null;
        }

        @Override
        public void set(CredentialRef ref, String value) {
            store.put(ref.value(), value);
        }

        @Override
        public void unset(CredentialRef ref) {
            store.remove(ref.value());
        }
    }
}