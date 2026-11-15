package net.linlan.dsh.infrastructure.adapter.llm.deepseek;

import net.linlan.dsh.domain.llm.adapter.ResolvedRetryPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("DeepSeek 图片上传策略")
class DeepSeekImageAttachmentPolicyTest {

    @Test
    @DisplayName("文件上传默认超时为 20s，连接超时为 5s")
    void defaultTimeouts() {
        var options = new DeepSeekAdapterOptions(
                "https://api.deepseek.com", "key", 4096, 128_000, 30_000,
                List.of(), new ResolvedRetryPolicy.Normal(0, 0, 0, 0, List.of()));

        assertEquals(20_000L, options.filesApiTimeoutMs());
        assertEquals(5_000L, options.filesConnectTimeoutMs());
        assertEquals(20_000L, DeepSeekAdapterOptions.DEFAULT_FILES_API_TIMEOUT_MS);
    }

    @Test
    @DisplayName("未配置上传器时使用内联 data URL")
    void inlineWithoutUploader() {
        var policy = new DeepSeekImageAttachmentPolicy(20_000, null);

        String url = policy.imageUrl(new byte[]{1, 2, 3}, "image/png");

        assertTrue(url.startsWith("data:image/png;base64,"));
        assertEquals(20_000, policy.currentTimeoutMs());
    }

    @Test
    @DisplayName("超时后降级内联并按 2 倍退避扩容")
    void timeoutFallsBackAndDoublesTimeout() {
        var policy = new DeepSeekImageAttachmentPolicy(20_000,
                (data, mime, timeout) -> {
                    throw new TimeoutException("slow");
                });

        assertTrue(policy.imageUrl(new byte[]{1}, "image/png").startsWith("data:"));
        assertEquals(40_000, policy.currentTimeoutMs());
        assertEquals(1, policy.consecutiveTimeouts());

        policy.imageUrl(new byte[]{1}, "image/png");
        assertEquals(80_000, policy.currentTimeoutMs());

        policy.imageUrl(new byte[]{1}, "image/png");
        assertEquals(160_000, policy.currentTimeoutMs());

        policy.imageUrl(new byte[]{1}, "image/png");
        policy.imageUrl(new byte[]{1}, "image/png");
        assertEquals(160_000, policy.currentTimeoutMs());
    }

    @Test
    @DisplayName("上传成功后复位超时与连续失败计数")
    void successResetsBackoff() {
        var failing = new boolean[]{true};
        var policy = new DeepSeekImageAttachmentPolicy(20_000,
                (data, mime, timeout) -> {
                    if (failing[0]) {
                        throw new TimeoutException("slow");
                    }
                    return "https://files.example.com/img-1";
                });

        policy.imageUrl(new byte[]{1}, "image/png");
        assertEquals(40_000, policy.currentTimeoutMs());

        failing[0] = false;
        String url = policy.imageUrl(new byte[]{1}, "image/png");

        assertEquals("https://files.example.com/img-1", url);
        assertEquals(20_000, policy.currentTimeoutMs());
        assertEquals(0, policy.consecutiveTimeouts());
    }

    @Test
    @DisplayName("非超时异常降级内联且不改变超时预算")
    void runtimeFailureFallsBack() {
        var policy = new DeepSeekImageAttachmentPolicy(20_000,
                (data, mime, timeout) -> {
                    throw new IllegalStateException("boom");
                });

        assertTrue(policy.imageUrl(new byte[]{1}, "image/png").startsWith("data:"));
        assertEquals(20_000, policy.currentTimeoutMs());
        assertEquals(0, policy.consecutiveTimeouts());
    }
}
