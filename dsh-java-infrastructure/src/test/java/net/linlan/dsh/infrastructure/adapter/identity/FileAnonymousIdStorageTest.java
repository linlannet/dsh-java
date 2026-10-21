package net.linlan.dsh.infrastructure.adapter.identity;

import net.linlan.dsh.domain.identity.model.valobj.AnonymousUserId;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FileAnonymousIdStorageTest 文件匿名标识存储单元测试。
 *
 * <p>使用 JUnit @TempDir 隔离文件系统副作用，覆盖首次读空、写入往返、跨实例保留、
 * 删除后读空、多路径隔离，以及 home 不可写时的降级（不抛异常、不附加 UUID）。</p>
 */
@DisplayName("FileAnonymousIdStorage 文件匿名标识存储")
public class FileAnonymousIdStorageTest {

    private static final String FILE_NAME = ".anonymous-user-id";

    // 场景：存储文件尚未创建时，首次 read 返回 empty 且 exists 为 false
    @Test
    @DisplayName("首次 read 返回 empty")
    void firstReadIsEmpty(@TempDir Path dir) {
        FileAnonymousIdStorage storage = new FileAnonymousIdStorage(dir.resolve(FILE_NAME));

        assertTrue(storage.read().isEmpty(), "文件不存在时应读取为空");
        assertFalse(storage.exists(), "文件不存在时 exists 应为 false");
    }

    // 场景：write 后应自动创建父目录、exists 为 true，且 read 往返得到同一标识
    @Test
    @DisplayName("write 后 read 往返一致且 exists 为 true")
    void writeThenReadRoundTrip(@TempDir Path dir) {
        // 父目录 nested 不存在，验证写入时自动创建
        FileAnonymousIdStorage storage = new FileAnonymousIdStorage(dir.resolve("nested").resolve(FILE_NAME));
        AnonymousUserId id = AnonymousUserId.generate();

        storage.write(id);

        assertTrue(storage.exists(), "写入后文件应存在");
        assertEquals(id, storage.read().orElseThrow(), "读回的标识应与写入一致");
        assertTrue(Files.exists(dir.resolve("nested").resolve(FILE_NAME)), "父目录应被自动创建");
    }

    // 场景：跨实例（重新构造同一路径）应能读到先前写入的标识
    @Test
    @DisplayName("跨实例保留已写入标识")
    void survivesAcrossInstances(@TempDir Path dir) {
        Path storagePath = dir.resolve(FILE_NAME);
        AnonymousUserId id = AnonymousUserId.of("fixed-anonymous-id");

        new FileAnonymousIdStorage(storagePath).write(id);

        FileAnonymousIdStorage reopened = new FileAnonymousIdStorage(storagePath);
        assertEquals(id, reopened.read().orElseThrow(), "新实例应读到同一标识");
        assertEquals("fixed-anonymous-id", reopened.read().orElseThrow().value(), "标识内容应保持一致");
    }

    // 场景：文件被删除后 read 重新返回 empty，exists 恢复为 false
    @Test
    @DisplayName("删除文件后 read 返回 empty")
    void readEmptyAfterFileDeleted(@TempDir Path dir) throws IOException {
        Path storagePath = dir.resolve(FILE_NAME);
        FileAnonymousIdStorage storage = new FileAnonymousIdStorage(storagePath);
        storage.write(AnonymousUserId.of("to-be-deleted"));
        assertTrue(storage.read().isPresent(), "删除前应能读到标识");

        Files.delete(storagePath);

        assertTrue(storage.read().isEmpty(), "删除后应读取为空");
        assertFalse(storage.exists(), "删除后 exists 应为 false");
    }

    // 场景：不同存储路径之间互不干扰
    @Test
    @DisplayName("不同路径互不干扰")
    void differentPathsAreIsolated(@TempDir Path dir) {
        FileAnonymousIdStorage storageA = new FileAnonymousIdStorage(dir.resolve("a").resolve(FILE_NAME));
        FileAnonymousIdStorage storageB = new FileAnonymousIdStorage(dir.resolve("b").resolve(FILE_NAME));

        storageA.write(AnonymousUserId.of("id-a"));

        assertTrue(storageB.read().isEmpty(), "未写入的另一路径应读取为空");
        assertFalse(storageB.exists(), "未写入的另一路径不应存在文件");

        storageB.write(AnonymousUserId.of("id-b"));

        assertEquals("id-a", storageA.read().orElseThrow().value(), "路径 A 内容应保持不变");
        assertEquals("id-b", storageB.read().orElseThrow().value(), "路径 B 内容应独立写入");
    }

    // 场景：文件内容为空白时视为无有效标识，read 返回 empty
    @Test
    @DisplayName("空白内容视为无标识")
    void blankContentReadsAsEmpty(@TempDir Path dir) throws IOException {
        Path storagePath = dir.resolve(FILE_NAME);
        Files.writeString(storagePath, "   ");

        FileAnonymousIdStorage storage = new FileAnonymousIdStorage(storagePath);

        assertTrue(storage.exists(), "文件物理存在");
        assertTrue(storage.read().isEmpty(), "空白内容不应解析出标识");
    }

    // 场景：读取时去除首尾空白，保证标识内容规范化
    @Test
    @DisplayName("读取时去除首尾空白")
    void readTrimsSurroundingWhitespace(@TempDir Path dir) {
        Path storagePath = dir.resolve(FILE_NAME);
        new FileAnonymousIdStorage(storagePath).write(AnonymousUserId.of("id-with-newline\n"));

        AnonymousUserId loaded = new FileAnonymousIdStorage(storagePath).read().orElseThrow();

        assertEquals("id-with-newline", loaded.value(), "读回应去除尾随换行");
    }

    // 场景：storagePath 本身是目录时读取失败，降级返回 empty 而非抛出异常
    @Test
    @DisplayName("路径为目录时降级返回 empty")
    void readFailsGracefullyWhenPathIsDirectory(@TempDir Path dir) throws IOException {
        Path storagePath = dir.resolve("dir-as-storage").resolve(FILE_NAME);
        Files.createDirectories(storagePath);

        FileAnonymousIdStorage storage = new FileAnonymousIdStorage(storagePath);

        assertTrue(storage.exists(), "目录物理存在");
        assertTrue(storage.read().isEmpty(), "读取目录失败时应降级返回 empty");
    }

    // 场景：父目录不可创建时 write 不抛异常并降级（不写出文件、无有效标识）
    @Test
    @DisplayName("父目录不可创建时 write 降级不抛异常")
    void writeFailsGracefullyWhenParentUncreatable() {
        // /dev/null 是字符设备，无法作为目录使用，父目录创建必然失败
        Path unwritable = Path.of("/dev/null").resolve("dsh-unwritable").resolve(FILE_NAME);
        FileAnonymousIdStorage storage = new FileAnonymousIdStorage(unwritable);

        assertDoesNotThrow(() -> storage.write(AnonymousUserId.of("x")), "写入失败应被捕获而非抛出");
        assertFalse(storage.exists(), "写入失败后文件不应存在");
        assertTrue(storage.read().isEmpty(), "写入失败后读取应为空");
    }

    // 场景：String 构造器以 dshHome 为根拼出 .anonymous-user-id 路径
    @Test
    @DisplayName("String 构造器基于 dshHome 拼路径")
    void stringConstructorUsesDshHome(@TempDir Path home) {
        FileAnonymousIdStorage storage = new FileAnonymousIdStorage(home.toString());

        storage.write(AnonymousUserId.of("home-id"));

        assertTrue(Files.exists(home.resolve(FILE_NAME)), "应在 dshHome 下创建标识文件");
        assertEquals("home-id", storage.read().orElseThrow().value(), "应读到 dshHome 下的标识");
    }
}