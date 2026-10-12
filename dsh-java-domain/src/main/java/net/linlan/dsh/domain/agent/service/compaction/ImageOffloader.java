package net.linlan.dsh.domain.agent.service.compaction;

import net.linlan.dsh.domain.model.valobj.ContentBlock;
import net.linlan.dsh.domain.model.valobj.ImageBlock;
import net.linlan.dsh.domain.model.valobj.TextBlock;
import net.linlan.dsh.domain.storage.adapter.port.IStorageBackendPort;
import net.linlan.dsh.domain.storage.model.valobj.KvUnitDescriptor;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ImageOffloader 将图像内容卸载到外部存储并替换为占位符。
 * <p>压缩触发时，将 ImageBlock 替换为占位符 TextBlock，记录可恢复引用。
 */
public final class ImageOffloader {

    private static final String PLACEHOLDER_PREFIX = "[image-offloaded:";
    private static final String PLACEHOLDER_SUFFIX = "]";

    private final IStorageBackendPort storage;
    private final KvUnitDescriptor descriptor;

    public ImageOffloader(IStorageBackendPort storage, KvUnitDescriptor descriptor) {
        this.storage = storage;
        this.descriptor = descriptor;
    }

    public ImageOffloader(IStorageBackendPort storage) {
        this(storage, null);
    }

    /**
     * 卸载内容块中的图像，替换为占位符。
     *
     * @param contentBlocks 原始内容块列表
     * @return 卸载结果
     */
    public OffloadResult offload(List<ContentBlock> contentBlocks) {
        if (contentBlocks == null || contentBlocks.isEmpty()) {
            return new OffloadResult(List.of(), Map.of());
        }
        List<ContentBlock> replaced = new ArrayList<>();
        Map<String, ImageBlock> offloaded = new LinkedHashMap<>();

        for (ContentBlock block : contentBlocks) {
            if (block instanceof ImageBlock imageBlock) {
                String refId = UUID.randomUUID().toString();
                offloaded.put(refId, imageBlock);
                storeImage(refId, imageBlock);
                String placeholder = PLACEHOLDER_PREFIX + refId + PLACEHOLDER_SUFFIX;
                replaced.add(new TextBlock(placeholder));
            } else {
                replaced.add(block);
            }
        }
        return new OffloadResult(List.copyOf(replaced), Map.copyOf(offloaded));
    }

    /**
     * 恢复占位符为原始图像内容。
     *
     * @param result 卸载结果
     * @return 恢复后的内容块列表
     */
    public List<ContentBlock> restore(OffloadResult result) {
        if (result == null) {
            return List.of();
        }
        List<ContentBlock> restored = new ArrayList<>();
        for (ContentBlock block : result.replaced()) {
            if (block instanceof TextBlock tb) {
                String refId = extractRefId(tb.text());
                if (refId != null && result.offloaded().containsKey(refId)) {
                    restored.add(result.offloaded().get(refId));
                } else {
                    restored.add(block);
                }
            } else {
                restored.add(block);
            }
        }
        return restored;
    }

    private void storeImage(String refId, ImageBlock imageBlock) {
        if (storage == null || descriptor == null) {
            return;
        }
        try {
            byte[] data = imageBlock.attachment().data();
            String encoded = Base64.getEncoder().encodeToString(data);
            storage.put(descriptor, "image_offload", refId, encoded);
        } catch (RuntimeException ignored) {
        }
    }

    private String extractRefId(String text) {
        if (text == null || !text.startsWith(PLACEHOLDER_PREFIX) || !text.endsWith(PLACEHOLDER_SUFFIX)) {
            return null;
        }
        int start = PLACEHOLDER_PREFIX.length();
        int end = text.length() - PLACEHOLDER_SUFFIX.length();
        return text.substring(start, end);
    }

    /**
     * OffloadResult 表示卸载操作的结果。
     *
     * @param replaced  替换后的内容块列表（图像变为占位符）
     * @param offloaded 被卸载的图像映射（refId → ImageBlock）
     */
    public record OffloadResult(
            List<ContentBlock> replaced,
            Map<String, ImageBlock> offloaded
    ) {
        public OffloadResult {
            replaced = replaced == null ? List.of() : List.copyOf(replaced);
            offloaded = offloaded == null ? Map.of() : Map.copyOf(offloaded);
        }

        public int offloadedCount() {
            return offloaded.size();
        }
    }
}
