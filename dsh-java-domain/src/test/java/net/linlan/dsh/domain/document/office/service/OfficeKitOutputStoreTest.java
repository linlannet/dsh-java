package net.linlan.dsh.domain.document.office.service;

import net.linlan.dsh.domain.document.office.model.valobj.OfficeKitConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Office 输出治理")
class OfficeKitOutputStoreTest {

    @Test
    @DisplayName("清理超过保留时长的产物")
    void prunesExpiredOutputs(@TempDir Path tempDir) throws Exception {
        Path oldFile = Files.writeString(tempDir.resolve("old.pdf"), "old");
        Path newFile = Files.writeString(tempDir.resolve("new.pdf"), "new");
        Files.setLastModifiedTime(oldFile,
                FileTime.from(Instant.now().minusMillis(10_000)));

        var config = new OfficeKitConfig(tempDir.toString(), 1_000, 1_000_000, null);
        new OfficeKitOutputStore(config).prune();

        assertFalse(Files.exists(oldFile));
        assertTrue(Files.exists(newFile));
    }

    @Test
    @DisplayName("超过容量上限时优先删除最旧产物")
    void prunesOldestWhenOverCapacity(@TempDir Path tempDir) throws Exception {
        Path first = Files.writeString(tempDir.resolve("first.pdf"), "0123456789");
        Path second = Files.writeString(tempDir.resolve("second.pdf"), "0123456789");
        Files.setLastModifiedTime(first, FileTime.from(Instant.now().minusMillis(10_000)));

        var config = new OfficeKitConfig(tempDir.toString(), 60_000, 10, null);
        new OfficeKitOutputStore(config).prune();

        assertFalse(Files.exists(first));
        assertTrue(Files.exists(second));
    }

    @Test
    @DisplayName("输出目录不存在时静默跳过")
    void missingDirectoryIsIgnored(@TempDir Path tempDir) {
        var config = new OfficeKitConfig(tempDir.resolve("missing").toString(), 1_000, 1_000, null);
        new OfficeKitOutputStore(config).prune();
    }
}
