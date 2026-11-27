package net.linlan.dsh.infrastructure.adapter.llm.deepseek;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

/** 将请求图片编码到软目标附近，并保证输出不超过字节硬上限。 */
public final class ImageEncoder {

    public static final int MAX_IMAGE_BYTES = 32 * 1024 * 1024;
    public static final int DEFAULT_IMAGE_TARGET_BYTES = 2 * 1024 * 1024;

    private static final int[] QUALITIES = {85, 75, 60};
    private static final double MIN_SHRINK_FACTOR = 1.1;
    private static final double BYTE_HEADROOM = 0.95;

    private final int maxImageBytes;
    private final int imageTargetBytes;

    public ImageEncoder() {
        this(MAX_IMAGE_BYTES, DEFAULT_IMAGE_TARGET_BYTES);
    }

    public ImageEncoder(int maxImageBytes, int imageTargetBytes) {
        if (maxImageBytes <= 0) {
            throw new IllegalArgumentException("maxImageBytes must be positive");
        }
        validateTarget(maxImageBytes, imageTargetBytes);
        this.maxImageBytes = maxImageBytes;
        this.imageTargetBytes = imageTargetBytes;
    }

    /** 编码后的图片及实际 MIME 类型。 */
    public record EncodedImage(byte[] data, String mimeType) {
        public EncodedImage {
            data = data == null ? new byte[0] : data.clone();
        }

        @Override
        public byte[] data() {
            return data.clone();
        }
    }

    public EncodedImage encode(byte[] data, String mimeType) {
        return encode(data, mimeType, imageTargetBytes);
    }

    /**
     * 使用本次模型请求的软目标编码图片。
     * 软目标不强制拒绝图片；只有超过 {@code maxImageBytes} 的结果会继续缩小或失败。
     */
    public EncodedImage encode(byte[] data, String mimeType, int imageTargetBytes) {
        validateTarget(maxImageBytes, imageTargetBytes);
        byte[] sourceBytes = data == null ? new byte[0] : data;
        String sourceMime = mimeType == null || mimeType.isBlank() ? "image/png" : mimeType;
        if (sourceBytes.length <= imageTargetBytes) {
            return new EncodedImage(sourceBytes, sourceMime);
        }

        BufferedImage source = read(sourceBytes);
        if (source == null) {
            if (sourceBytes.length <= maxImageBytes) {
                return new EncodedImage(sourceBytes, sourceMime);
            }
            throw tooLarge("Image exceeds the " + maxImageBytes
                    + "-byte request limit and cannot be decoded for compression.", null);
        }

        boolean hasAlpha = source.getColorModel().hasAlpha();
        int width = source.getWidth();
        int height = source.getHeight();
        while (true) {
            BufferedImage sized = width == source.getWidth() && height == source.getHeight()
                    ? source : resize(source, width, height, hasAlpha);
            EncodedImage smallest = null;
            for (int quality : QUALITIES) {
                EncodedImage candidate = write(sized, hasAlpha, quality);
                if (candidate.data.length <= imageTargetBytes) {
                    return candidate;
                }
                if (smallest == null || candidate.data.length < smallest.data.length) {
                    smallest = candidate;
                }
            }
            if (smallest != null && smallest.data.length <= maxImageBytes) {
                return smallest;
            }

            int longEdge = Math.max(width, height);
            if (longEdge <= 1) {
                int actualBytes = smallest == null ? sourceBytes.length : smallest.data.length;
                throw tooLarge("Image still exceeds the " + maxImageBytes
                        + "-byte request limit at 1x1 pixels (encoded bytes: " + actualBytes + ").", null);
            }
            int actualBytes = smallest == null ? sourceBytes.length : smallest.data.length;
            double divisor = Math.max(MIN_SHRINK_FACTOR,
                    Math.sqrt(actualBytes / (BYTE_HEADROOM * maxImageBytes)));
            int nextEdge = Math.max(1, Math.min(longEdge - 1, (int) Math.floor(longEdge / divisor)));
            double scale = (double) nextEdge / longEdge;
            width = Math.max(1, (int) Math.floor(width * scale));
            height = Math.max(1, (int) Math.floor(height * scale));
            if (width == 1 || height == 1) {
                if (width >= height) {
                    height = 1;
                } else {
                    width = 1;
                }
            }
        }
    }

    public int maxImageBytes() {
        return maxImageBytes;
    }

    public int imageTargetBytes() {
        return imageTargetBytes;
    }

    private static void validateTarget(int maxImageBytes, int imageTargetBytes) {
        if (imageTargetBytes <= 0 || imageTargetBytes > maxImageBytes) {
            throw new IllegalArgumentException("imageTargetBytes must be positive and no greater than maxImageBytes");
        }
    }

    private static BufferedImage read(byte[] data) {
        try {
            return ImageIO.read(new ByteArrayInputStream(data));
        } catch (IOException | RuntimeException exception) {
            return null;
        }
    }

    private static BufferedImage resize(BufferedImage source, int width, int height, boolean hasAlpha) {
        BufferedImage resized = new BufferedImage(width, height,
                hasAlpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = resized.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return resized;
    }

    private static EncodedImage write(BufferedImage image, boolean hasAlpha, int quality) {
        String format = hasAlpha ? "png" : "jpeg";
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(format);
        if (!writers.hasNext()) {
            throw tooLarge("No JDK ImageIO writer is available for " + format + ".", null);
        }
        ImageWriter writer = writers.next();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ImageOutputStream stream = ImageIO.createImageOutputStream(output)) {
            writer.setOutput(stream);
            ImageWriteParam parameters = writer.getDefaultWriteParam();
            if (parameters.canWriteCompressed()) {
                parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                String[] types = parameters.getCompressionTypes();
                if (types != null && types.length > 0) {
                    parameters.setCompressionType(types[0]);
                }
                parameters.setCompressionQuality(quality / 100.0f);
            }
            writer.write(null, new IIOImage(image, null, null), parameters);
            stream.flush();
        } catch (IOException | RuntimeException exception) {
            throw tooLarge("Unable to encode the request image within its byte limit.", exception);
        } finally {
            writer.dispose();
        }
        return new EncodedImage(output.toByteArray(), hasAlpha ? "image/png" : "image/jpeg");
    }

    private static DeepSeekError tooLarge(String message, Throwable cause) {
        return new DeepSeekError(DeepSeekError.IMAGE_TARGET_BYTES_EXCEEDED, message, cause);
    }
}
