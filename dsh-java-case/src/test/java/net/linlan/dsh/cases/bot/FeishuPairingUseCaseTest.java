package net.linlan.dsh.cases.bot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import net.linlan.dsh.api.dto.FeishuPairingDTO;
import net.linlan.dsh.api.dto.FeishuPairingStartDTO;
import net.linlan.dsh.api.response.Response;
import net.linlan.dsh.domain.bot.adapter.port.IFeishuBotConnectionPort;
import net.linlan.dsh.domain.bot.adapter.port.IFeishuPairingPort;
import net.linlan.dsh.domain.bot.adapter.repository.IFeishuBotRepository;
import net.linlan.dsh.domain.bot.model.aggregate.FeishuBotAggregate;
import net.linlan.dsh.domain.bot.model.valobj.FeishuConnectionState;
import net.linlan.dsh.domain.bot.model.valobj.FeishuPairingBeginVO;
import net.linlan.dsh.domain.bot.model.valobj.FeishuPairingPollVO;
import net.linlan.dsh.domain.credentials.adapter.port.ICredentialProviderPort;
import net.linlan.dsh.domain.credentials.model.valobj.CredentialInfoVO;
import net.linlan.dsh.domain.credentials.model.valobj.CredentialRef;
import net.linlan.dsh.domain.credentials.model.valobj.ResolvedCredentialVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 一键配对用例测试：以桩端口驱动 begin/poll 状态机，校验等待、成功建 Bot、拒绝、过期与取消。
 */
class FeishuPairingUseCaseTest {

    private FakePairingPort pairingPort;
    private FakeRepository repository;
    private FakeConnectionPort connectionPort;
    private FakeCredentialPort credentialPort;
    private FeishuPairingUseCase useCase;

    @BeforeEach
    void setUp() throws Exception {
        this.pairingPort = new FakePairingPort();
        this.repository = new FakeRepository();
        this.connectionPort = new FakeConnectionPort();
        this.credentialPort = new FakeCredentialPort();
        this.useCase = new FeishuPairingUseCase();
        this.inject("pairingPort", this.pairingPort);
        this.inject("botRepository", this.repository);
        this.inject("connectionPort", this.connectionPort);
        this.inject("credentialPort", this.credentialPort);
    }

    private void inject(String field, Object value) throws Exception {
        Field target = FeishuPairingUseCase.class.getDeclaredField(field);
        target.setAccessible(true);
        target.set(this.useCase, value);
    }

    private FeishuPairingDTO start() {
        Response<FeishuPairingDTO> resp = this.useCase.startPairing(new FeishuPairingStartDTO(null, null));
        assertEquals(Response.SUCCESS_CODE, resp.code(), resp.info());
        return resp.data();
    }

    private FeishuPairingDTO awaitStatus(String pairingId, String expected) {
        long deadline = System.currentTimeMillis() + 10000L;
        FeishuPairingDTO last = null;
        while (System.currentTimeMillis() < deadline) {
            Response<FeishuPairingDTO> resp = this.useCase.pairingStatus(pairingId);
            assertEquals(Response.SUCCESS_CODE, resp.code());
            last = resp.data();
            if (expected.equals(last.status())) {
                return last;
            }
            try {
                Thread.sleep(100L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return last;
    }

    @Test
    void startPairingUsesDefaultsAndReportsWaiting() {
        FeishuPairingDTO dto = this.start();

        assertEquals("WAITING", dto.status());
        assertEquals("https://accounts.feishu.cn/sign", dto.url());
        assertEquals("ABCD", dto.userCode());
        assertTrue(dto.expireIn() > 0);
        assertEquals("DeepSeek Bot", this.pairingPort.lastBeginName);
        assertEquals("手机上对话电脑端 Harness Agent，支持工具调用与流式回复", this.pairingPort.lastBeginDesc);
    }

    @Test
    void pollingSuccessCreatesBotAndGoesOnline() {
        this.pairingPort.enqueue(new FeishuPairingPollVO(null, null, null, null, "authorization_pending", null));
        this.pairingPort.enqueue(new FeishuPairingPollVO("cli_new", "sec_new", "ou_1", "feishu", null, null));

        FeishuPairingDTO dto = this.awaitStatus(this.start().pairingId(), "SAVED");

        assertEquals("SAVED", dto.status());
        assertEquals("cli_new", dto.appId());
        assertEquals("deepseek-bot", dto.botId());
        assertEquals(1, this.repository.store.size());
        FeishuBotAggregate bot = this.repository.store.get("deepseek-bot");
        assertTrue(bot.isEnabled());
        assertEquals("feishu-agent", bot.getAgentBaseId());
        assertEquals("FEISHU_BOT_DEEPSEEK_BOT_APP_SECRET", bot.getSecretRef());
        assertEquals("sec_new", this.credentialPort.store.get("FEISHU_BOT_DEEPSEEK_BOT_APP_SECRET"));
        assertEquals(1, this.connectionPort.started.size(), "成功后应拉起长连接");
    }

    @Test
    void accessDeniedFailsFast() {
        this.pairingPort.enqueue(new FeishuPairingPollVO(null, null, null, null, "access_denied", null));

        FeishuPairingDTO dto = this.awaitStatus(this.start().pairingId(), "FAILED");

        assertEquals("FAILED", dto.status());
        assertEquals("用户拒绝了授权", dto.error());
        assertTrue(this.repository.store.isEmpty());
    }

    @Test
    void slowDownKeepsSessionWaiting() {
        this.pairingPort.enqueue(new FeishuPairingPollVO(null, null, null, null, "slow_down", null));
        for (int i = 0; i < 20; i++) {
            this.pairingPort.enqueue(new FeishuPairingPollVO(null, null, null, null, "authorization_pending", null));
        }

        FeishuPairingDTO dto = this.start();
        try {
            Thread.sleep(1200L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        Response<FeishuPairingDTO> resp = this.useCase.pairingStatus(dto.pairingId());

        assertEquals("WAITING", resp.data().status(), "slow_down 应仅放宽间隔而不失败");
        assertTrue(this.pairingPort.pollCount.get() >= 1);
    }

    @Test
    void cancelPairingDropsSession() {
        FeishuPairingDTO dto = this.start();

        Response<Void> cancelled = this.useCase.cancelPairing(dto.pairingId());
        assertEquals(Response.SUCCESS_CODE, cancelled.code());

        Response<FeishuPairingDTO> status = this.useCase.pairingStatus(dto.pairingId());
        assertEquals(Response.INVALID_ARGUMENT_CODE, status.code());
    }

    @Test
    void unknownPairingIdIsInvalidArgument() {
        Response<FeishuPairingDTO> status = this.useCase.pairingStatus("nope");
        assertEquals(Response.INVALID_ARGUMENT_CODE, status.code());
    }

    @Test
    void beginFailureIsReportedAsInternalError() {
        this.pairingPort.failBegin = true;
        Response<FeishuPairingDTO> resp = this.useCase.startPairing(new FeishuPairingStartDTO("Bot", "base"));

        assertEquals(Response.INTERNAL_ERROR_CODE, resp.code());
        assertTrue(resp.info().contains("发起一键绑定失败"));
    }

    /**
     * 桩配对端口：可排队轮询结果。
     */
    private static final class FakePairingPort implements IFeishuPairingPort {

        private final List<FeishuPairingPollVO> queue = new ArrayList<>();
        private final AtomicInteger pollCount = new AtomicInteger();
        private String lastBeginName;
        private String lastBeginDesc;
        private boolean failBegin;

        private void enqueue(FeishuPairingPollVO vo) {
            this.queue.add(vo);
        }

        @Override
        public FeishuPairingBeginVO begin(String appName, String appDesc) {
            this.lastBeginName = appName;
            this.lastBeginDesc = appDesc;
            if (this.failBegin) {
                throw new IllegalStateException("端点不可用");
            }
            return new FeishuPairingBeginVO("https://accounts.feishu.cn/sign", "ABCD", "dv_1", 60L, 1);
        }

        @Override
        public FeishuPairingPollVO poll(String baseUrl, String deviceCode) {
            int index = this.pollCount.getAndIncrement();
            if (index < this.queue.size()) {
                return this.queue.get(index);
            }
            return new FeishuPairingPollVO(null, null, null, null, "authorization_pending", null);
        }

        @Override
        public String larkBaseUrl() {
            return "https://accounts.larksuite.com";
        }
    }

    /**
     * 内存仓储桩。
     */
    private static final class FakeRepository implements IFeishuBotRepository {

        private final Map<String, FeishuBotAggregate> store = new LinkedHashMap<>();

        @Override
        public void save(FeishuBotAggregate bot) {
            this.store.put(bot.getBotId(), bot);
        }

        @Override
        public Optional<FeishuBotAggregate> findByBotId(String botId) {
            return Optional.ofNullable(this.store.get(botId));
        }

        @Override
        public List<FeishuBotAggregate> findAll() {
            return new ArrayList<>(this.store.values());
        }

        @Override
        public void deleteByBotId(String botId) {
            this.store.remove(botId);
        }
    }

    /**
     * 记录建连调用的桩连接端口。
     */
    private static final class FakeConnectionPort implements IFeishuBotConnectionPort {

        private final List<String> started = new ArrayList<>();

        @Override
        public void start(FeishuBotAggregate bot) {
            this.started.add(bot.getBotId());
        }

        @Override
        public void stop(String botId) {
            this.started.remove(botId);
        }

        @Override
        public boolean isConnected(String botId) {
            return this.started.contains(botId);
        }

        @Override
        public String stateOf(String botId) {
            return this.started.contains(botId) ? FeishuConnectionState.CONNECTED.name() : FeishuConnectionState.IDLE.name();
        }

        @Override
        public String lastErrorOf(String botId) {
            return null;
        }
    }

    /**
     * 内存凭据端口桩。
     */
    private static final class FakeCredentialPort implements ICredentialProviderPort {

        private final Map<String, String> store = new HashMap<>();

        @Override
        public Optional<ResolvedCredentialVO> resolve(CredentialRef ref) {
            String value = this.store.get(ref.value());
            return value == null ? Optional.empty() : Optional.of(new ResolvedCredentialVO(value, "test"));
        }

        @Override
        public CredentialInfoVO describe(CredentialRef ref) {
            return new CredentialInfoVO(this.store.containsKey(ref.value()), Optional.of("test"), true);
        }

        @Override
        public void set(CredentialRef ref, String value) {
            this.store.put(ref.value(), value);
        }

        @Override
        public void unset(CredentialRef ref) {
            this.store.remove(ref.value());
        }
    }
}
