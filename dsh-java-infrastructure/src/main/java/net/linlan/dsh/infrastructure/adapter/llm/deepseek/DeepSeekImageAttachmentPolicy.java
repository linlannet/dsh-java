package net.linlan.dsh.infrastructure.adapter.llm.deepseek;

import java.util.Base64;
import java.util.Objects;
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
    private final DeepSeekFileStore fileStore;
    private final DeepSeekFilePolicy filePolicy;
    private final ImageEncoder imageEncoder;
    private long currentTimeoutMs;
    private int consecutiveTimeouts;

    public DeepSeekImageAttachmentPolicy(long baseTimeoutMs, FileUploader uploader) {
        this(baseTimeoutMs, uploader, null, null, new ImageEncoder());
    }

    /** 使用显式文件存储和已解析策略；文件存储调用不会自行补默认策略。 */
    public DeepSeekImageAttachmentPolicy(DeepSeekFileStore fileStore, DeepSeekFilePolicy filePolicy) {
        this(requirePolicy(filePolicy).uploadTimeoutMs(), null,
                Objects.requireNonNull(fileStore, "fileStore"), filePolicy, new ImageEncoder());
    }

    private DeepSeekImageAttachmentPolicy(long baseTimeoutMs, FileUploader uploader,
                                          DeepSeekFileStore fileStore, DeepSeekFilePolicy filePolicy,
                                          ImageEncoder imageEncoder) {
        this.baseTimeoutMs = baseTimeoutMs > 0 ? baseTimeoutMs : DeepSeekAdapterOptions.DEFAULT_FILES_API_TIMEOUT_MS;
        this.currentTimeoutMs = this.baseTimeoutMs;
        this.uploader = uploader;
        this.fileStore = fileStore;
        this.filePolicy = filePolicy;
        this.imageEncoder = imageEncoder;
    }

    /**
     * 解析图片 URL。
     *
     * @param data     图片字节
     * @param mimeType MIME 类型
     * @return Files API URL 或内联 data URL
     */
    public String imageUrl(byte[] data, String mimeType) {
        return imageUrl(data, mimeType, ImageEncoder.DEFAULT_IMAGE_TARGET_BYTES);
    }

    /** 使用当前模型解析出的 imageTargetBytes 软目标编码后上传或内联。 */
    public String imageUrl(byte[] data, String mimeType, int imageTargetBytes) {
        ImageEncoder.EncodedImage encoded = imageEncoder.encode(data, mimeType, imageTargetBytes);
        byte[] bytes = encoded.data();
        String mime = encoded.mimeType();
        if (uploader == null && fileStore == null) {
            return inlineUrl(bytes, mime);
        }
        try {
            String url = fileStore != null
                    ? fileStore.ensureUploaded(bytes, mime, currentTimeoutMs, filePolicy)
                    : uploader.upload(bytes, mime, currentTimeoutMs);
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

    private static DeepSeekFilePolicy requirePolicy(DeepSeekFilePolicy policy) {
        if (policy == null) {
            throw DeepSeekError.missingFilePolicy();
        }
        return policy;
    }

    private void reset() {
        currentTimeoutMs = baseTimeoutMs;
        consecutiveTimeouts = 0;
    }

    private static String inlineUrl(byte[] data, String mime) {
        return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(data);
    }
}
