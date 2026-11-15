package net.linlan.dsh.domain.document.office.service;

import net.linlan.dsh.domain.document.office.model.valobj.OfficeKitConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

/**
 * OfficeKitOutputStore 负责 Office 输出产物的过期与容量治理。
 *
 * <p>调用流程：工具生成产物 → prune 按保留时长/容量上限清理 → 返回最新产物。</p>
 * <p>示例：超过 24 小时的 PDF 会被删除，超过容量上限时优先删除最旧文件。</p>
 */
public final class OfficeKitOutputStore {

    private final OfficeKitConfig config;

    public OfficeKitOutputStore(OfficeKitConfig config) {
        this.config = config;
    }

    /**
     * 清理过期与超额产物，失败时静默跳过，避免影响工具主流程。
     */
    public void prune() {
        Path root = Path.of(config.outputDirectory());
        if (!Files.isDirectory(root)) {
            return;
        }
        long cutoff = System.currentTimeMillis() - config.outputRetentionMs();
        try (var stream = Files.walk(root)) {
            List<Path> files = stream.filter(Files::isRegularFile)
                    .sorted(Comparator.comparingLong(OfficeKitOutputStore::lastModified))
                    .toList();
            long total = 0L;
            for (Path file : files) {
                total += sizeOf(file);
            }
            for (Path file : files) {
                long size = sizeOf(file);
                if (lastModified(file) < cutoff || total > config.maxOutputStoreBytes()) {
                    if (delete(file)) {
                        total -= size;
                    }
                }
            }
        } catch (IOException ignored) {
            // 治理失败不影响产物返回
        }
    }

    private static long lastModified(Path file) {
        try {
            return Files.getLastModifiedTime(file).toMillis();
        } catch (IOException e) {
            return 0L;
        }
    }

    private static long sizeOf(Path file) {
        try {
            return Files.size(file);
        } catch (IOException e) {
            return 0L;
        }
    }

    private static boolean delete(Path file) {
        try {
            return Files.deleteIfExists(file);
        } catch (IOException e) {
            return false;
        }
    }
}
