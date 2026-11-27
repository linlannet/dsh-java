package net.linlan.dsh.infrastructure.adapter.llm.deepseek;

import java.util.Objects;
import java.util.concurrent.TimeoutException;

/** DeepSeek 文件存储入口；每次调用都要求调用方传入已解析策略。 */
public final class DeepSeekFileStore {

    /** 文件上传传输边界。 */
    @FunctionalInterface
    public interface UploadTransport {
        String upload(byte[] data, String mimeType, long timeoutMs, DeepSeekFilePolicy policy)
                throws TimeoutException;
    }

    private final UploadTransport transport;

    public DeepSeekFileStore(UploadTransport transport) {
        this.transport = Objects.requireNonNull(transport, "transport");
    }

    /**
     * 上传一份文件。
     *
     * @param data 文件字节
     * @param mimeType MIME 类型
     * @param timeoutMs 当前调用的超时预算
     * @param policy 已解析的上传策略，不得在存储内部补默认值
     * @return 可用于模型请求的文件 URL
     */
    public String ensureUploaded(byte[] data, String mimeType, long timeoutMs, DeepSeekFilePolicy policy)
            throws TimeoutException {
        if (policy == null) {
            throw DeepSeekError.missingFilePolicy();
        }
        return transport.upload(data, mimeType, timeoutMs, policy);
    }
}
