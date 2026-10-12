package net.linlan.dsh.domain.agent.service.compaction;

import net.linlan.dsh.domain.model.valobj.ContentBlock;
import net.linlan.dsh.domain.model.valobj.ImageAttachmentRef;
import net.linlan.dsh.domain.model.valobj.ImageBlock;
import net.linlan.dsh.domain.model.valobj.TextBlock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ImageOffloader 图像卸载")
class ImageOffloaderTest {

    @Test
    @DisplayName("无图像内容块不变")
    void noImageBlocksUnchanged() {
        var offloader = new ImageOffloader(null);
        var blocks = List.<ContentBlock>of(new TextBlock("hello"), new TextBlock("world"));
        var result = offloader.offload(blocks);
        assertEquals(2, result.replaced().size());
        assertEquals(0, result.offloadedCount());
    }

    @Test
    @DisplayName("图像被替换为占位符")
    void imageReplacedWithPlaceholder() {
        var offloader = new ImageOffloader(null);
        var image = new ImageBlock(new ImageAttachmentRef(new byte[]{1, 2, 3}, "image/png", 100, 100));
        var blocks = List.<ContentBlock>of(new TextBlock("text"), image);
        var result = offloader.offload(blocks);
        assertEquals(2, result.replaced().size());
        assertEquals(1, result.offloadedCount());
        assertTrue(result.replaced().get(1) instanceof TextBlock);
        assertTrue(((TextBlock) result.replaced().get(1)).text().contains("[image-offloaded:"));
    }

    @Test
    @DisplayName("restore 恢复图像")
    void restoreRecoversImages() {
        var offloader = new ImageOffloader(null);
        var image = new ImageBlock(new ImageAttachmentRef(new byte[]{1, 2, 3}, "image/png", 100, 100));
        var blocks = List.<ContentBlock>of(new TextBlock("text"), image);
        var offloaded = offloader.offload(blocks);
        var restored = offloader.restore(offloaded);
        assertEquals(2, restored.size());
        assertTrue(restored.get(1) instanceof ImageBlock);
    }

    @Test
    @DisplayName("空列表返回空结果")
    void emptyListReturnsEmpty() {
        var offloader = new ImageOffloader(null);
        var result = offloader.offload(List.of());
        assertEquals(0, result.replaced().size());
        assertEquals(0, result.offloadedCount());
    }

    @Test
    @DisplayName("null 列表返回空结果")
    void nullListReturnsEmpty() {
        var offloader = new ImageOffloader(null);
        var result = offloader.offload(null);
        assertEquals(0, result.replaced().size());
    }

    @Test
    @DisplayName("restore null 返回空列表")
    void restoreNullReturnsEmpty() {
        var offloader = new ImageOffloader(null);
        var restored = offloader.restore(null);
        assertTrue(restored.isEmpty());
    }

    @Test
    @DisplayName("多个图像全部卸载")
    void multipleImagesAllOffloaded() {
        var offloader = new ImageOffloader(null);
        var img1 = new ImageBlock(new ImageAttachmentRef(new byte[]{1}, "image/png", 10, 10));
        var img2 = new ImageBlock(new ImageAttachmentRef(new byte[]{2}, "image/jpeg", 20, 20));
        var blocks = List.<ContentBlock>of(img1, new TextBlock("between"), img2);
        var result = offloader.offload(blocks);
        assertEquals(3, result.replaced().size());
        assertEquals(2, result.offloadedCount());
    }
}
