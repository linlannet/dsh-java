package net.linlan.dsh.infrastructure.adapter.llm.deepseek;

import java.util.Base64;
import java.util.concurrent.TimeoutException;

/**
 * DeepSeekImageAttachmentPolicy 决定图片使用 Files API 还是内联 data URL。
 *
 * <p>调用流程：上传图片 → 成功返回 file URL → 超时降级内联并 2 倍退避 → 成功复位。</p>
 * <p>示例：首次超时后请求超时预算变为 40s，成功上传后复位为 20s。</p>
 */
public final class DeepSeekImageAttachmentPolicy {

    /** 退避上限倍数。 */
    private static final int MAX_BACKOFF_MULTIPLIER = 8;

    /**
     * 文件上传器：返回可放入 image_url 的 URL；超时抛出 {@link TimeoutException}。
     */
    @FunctionalInterface
    public interface FileUploader {
        String upload(byte[] data, String mimeType, long timeoutMs) throws TimeoutException;
    }

    private final long baseTimeoutMs;
    private final FileUploader uploader;
    private long currentTimeoutMs;
    private int consecutiveTimeouts;

    public DeepSeekImageAttachmentPolicy(long baseTimeoutMs, FileUploader uploader) {
        this.baseTimeoutMs = baseTimeoutMs > 0 ? baseTimeoutMs : DeepSeekAdapterOptions.DEFAULT_FILES_API_TIMEOUT_MS;
        this.currentTimeoutMs = this.baseTimeoutMs;
        this.uploader = uploader;
    }

    /**
     * 解析图片 URL。
     *
     * @param data     图片字节
     * @param mimeType MIME 类型
     * @return Files API URL 或内联 data URL
     */
    public String imageUrl(byte[] data, String mimeType) {
        byte[] bytes = data == null ? new byte[0] : data;
        String mime = mimeType == null || mimeType.isBlank() ? "image/png" : mimeType;
        if (uploader == null) {
            return inlineUrl(bytes, mime);
        }
        try {
            String url = uploader.upload(bytes, mime, currentTimeoutMs);
            reset();
            return url == null || url.isBlank() ? inlineUrl(bytes, mime) : url;
        } catch (TimeoutException e) {
            consecutiveTimeouts++;
            currentTimeoutMs = Math.min(baseTimeoutMs * MAX_BACKOFF_MULTIPLIER, currentTimeoutMs * 2);
            return inlineUrl(bytes, mime);
        } catch (RuntimeException e) {
            return inlineUrl(bytes, mime);
        }
    }

    /**
     * 当前上传超时预算。
     *
     * @return 超时毫秒
     */
    public long currentTimeoutMs() {
        return currentTimeoutMs;
    }

    /**
     * 连续超时次数。
     *
     * @return 次数
     */
    public int consecutiveTimeouts() {
        return consecutiveTimeouts;
    }

    private void reset() {
        currentTimeoutMs = baseTimeoutMs;
        consecutiveTimeouts = 0;
    }

    private static String inlineUrl(byte[] data, String mime) {
        return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(data);
    }
}
