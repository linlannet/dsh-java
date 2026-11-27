package net.linlan.dsh.infrastructure.adapter.llm.deepseek;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("请求图片编码限制")
class ImageEncoderTest {

    @Test
    @DisplayName("软目标以内保留原始图片")
    void keepsImageWithinSoftTarget() throws Exception {
        byte[] source = png(8, 8, 7);
        ImageEncoder encoder = new ImageEncoder(1024 * 1024, source.length);

        ImageEncoder.EncodedImage encoded = encoder.encode(source, "image/png");

        assertArrayEquals(source, encoded.data());
        assertEquals("image/png", encoded.mimeType());
    }

    @Test
    @DisplayName("超过软目标和硬上限时压降到硬上限以内")
    void compressesOversizedImageBelowHardLimit() throws Exception {
        byte[] source = png(384, 384, 42);
        assertTrue(source.length > 4096);
        ImageEncoder encoder = new ImageEncoder(4096, 1024);

        ImageEncoder.EncodedImage encoded = encoder.encode(source, "image/png");

        assertTrue(encoded.data().length <= 4096);
        assertNotNull(ImageIO.read(new ByteArrayInputStream(encoded.data())));
    }

    @Test
    @DisplayName("1x1 仍超过硬上限时编码失败")
    void rejectsImageThatCannotMeetHardLimit() throws Exception {
        byte[] source = png(4, 4, 9);
        ImageEncoder encoder = new ImageEncoder(1, 1);

        DeepSeekError error = assertThrows(DeepSeekError.class,
                () -> encoder.encode(source, "image/png"));

        assertEquals(DeepSeekError.IMAGE_TARGET_BYTES_EXCEEDED, error.errorCode());
        assertTrue(error.getMessage().contains("1x1"));
    }

    @Test
    @DisplayName("模型目录暴露 imageTargetBytes 且兼容旧构造器")
    void catalogSupportsSoftTarget() {
        DirectSeekCatalogModel model = new DirectSeekCatalogModel(
                "vision", "Vision", null, 128_000, 4096, 1024 * 1024);
        DirectSeekCatalogModel legacy = new DirectSeekCatalogModel(
                "text", "Text", null, 128_000, 4096);

        assertEquals(1024 * 1024, model.imageTargetBytesOpt().orElseThrow());
        assertTrue(legacy.imageTargetBytesOpt().isEmpty());
    }

    private static byte[] png(int width, int height, long seed) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Random random = new Random(seed);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, random.nextInt(0x1000000));
            }
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, "png", output));
        return output.toByteArray();
    }
}
