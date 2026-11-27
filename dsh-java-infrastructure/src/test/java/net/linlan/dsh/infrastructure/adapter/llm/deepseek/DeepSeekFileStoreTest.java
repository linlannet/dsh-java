package net.linlan.dsh.infrastructure.adapter.llm.deepseek;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DeepSeek 文件上传策略")
class DeepSeekFileStoreTest {

    @Test
    @DisplayName("每次存储调用显式传递同一个已解析策略")
    void passesResolvedPolicyToStore() throws Exception {
        DeepSeekFilePolicy resolved = DeepSeekFilePolicy.defaults(12_345);
        AtomicReference<DeepSeekFilePolicy> received = new AtomicReference<>();
        DeepSeekFileStore store = new DeepSeekFileStore((data, mimeType, timeoutMs, policy) -> {
            received.set(policy);
            assertEquals(12_345, timeoutMs);
            return "https://files.example.com/image-1";
        });
        DeepSeekImageAttachmentPolicy images = new DeepSeekImageAttachmentPolicy(store, resolved);

        String url = images.imageUrl(new byte[]{1, 2, 3}, "image/png");

        assertEquals("https://files.example.com/image-1", url);
        assertSame(resolved, received.get());
    }

    @Test
    @DisplayName("直接调用存储缺少策略时拒绝隐式默认")
    void rejectsMissingPolicy() {
        DeepSeekFileStore store = new DeepSeekFileStore((data, mimeType, timeoutMs, policy) -> "unused");

        DeepSeekError error = assertThrows(DeepSeekError.class,
                () -> store.ensureUploaded(new byte[]{1}, "image/png", 20_000, null));

        assertEquals(DeepSeekError.FILE_POLICY_INVALID, error.errorCode());
        assertTrue(error.getMessage().contains("resolved DeepSeekFilePolicy"));
    }
}
