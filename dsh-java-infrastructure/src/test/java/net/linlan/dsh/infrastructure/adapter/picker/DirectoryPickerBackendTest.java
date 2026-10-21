package net.linlan.dsh.infrastructure.adapter.picker;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.GraphicsEnvironment;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * DirectoryPickerBackendTest 目录选择后端单元测试。
 *
 * <p>覆盖同包下三个后端实现：NativeDirectoryPickerBackend（原生 OS 对话框）、
 * BrowseDirectoryPickerBackend（应用内浏览）、AutoDirectoryPickerBackend（自适应回退）。</p>
 *
 * <p>说明：原生对话框在非 headless 环境会阻塞测试进程，因此涉及真实弹窗的用例
 * 使用 JUnit Assumptions 在非 headless 环境下跳过；Auto 后端的协作行为通过覆盖
 * native 支持判定的测试替身来稳定验证，不依赖运行环境的图形能力。</p>
 */
@DisplayName("DirectoryPicker 后端（Native/Browse/Auto）")
public class DirectoryPickerBackendTest {

    private final NativeDirectoryPickerBackend nativeBackend = new NativeDirectoryPickerBackend();
    private final BrowseDirectoryPickerBackend browseBackend = new BrowseDirectoryPickerBackend();

    /**
     * 构造一个可控的 native 替身，用于驱动 Auto 后端的分支。
     *
     * @param supports    替身 supports() 的返回值
     * @param pickedValue 替身 pick() 返回的目录；为 null 时返回 Optional.empty()
     */
    private NativeDirectoryPickerBackend nativeStub(boolean supports, String pickedValue) {
        return new NativeDirectoryPickerBackend() {
            @Override
            public boolean supports() {
                return supports;
            }

            @Override
            public Optional<String> pick(String title, String initialPath) {
                return pickedValue == null ? Optional.empty() : Optional.of(pickedValue);
            }
        };
    }

    /** 构造一个永远不可用的 browse 替身。 */
    private BrowseDirectoryPickerBackend unavailableBrowseStub() {
        return new BrowseDirectoryPickerBackend() {
            @Override
            public boolean supports() {
                return false;
            }
        };
    }

    // ================= NativeDirectoryPickerBackend =================

    // 场景：native 后端可用性判定必须与当前图形环境是否 headless 严格相反
    @Test
    @DisplayName("Native supports 与 headless 状态严格相反")
    void nativeSupportsOpposesHeadlessState() {
        assertEquals(!GraphicsEnvironment.isHeadless(), nativeBackend.supports(),
                "图形环境可用时应支持，headless 时应不支持");
    }

    // 场景：headless 环境下 native 不可用，且 pick 直接返回 empty 而不弹出对话框
    @Test
    @DisplayName("headless 环境下 Native pick 直接返回 empty")
    void nativePickReturnsEmptyInHeadlessEnvironment() {
        assumeTrue(GraphicsEnvironment.isHeadless(),
                "非 headless 环境跳过，避免弹出原生对话框阻塞测试");

        assertFalse(nativeBackend.supports(), "headless 环境应判定为不可用");
        assertTrue(nativeBackend.pick("选择目录", null).isEmpty(), "headless 时 pick 应返回 empty");
        assertTrue(nativeBackend.pick(null, "/tmp").isEmpty(), "headless 时即使给定初始目录也返回 empty");
    }

    // ================= BrowseDirectoryPickerBackend =================

    // 场景：browse 后端面向远程客户端，任何环境都应声明支持
    @Test
    @DisplayName("Browse supports 恒为 true")
    void browseAlwaysSupports() {
        assertTrue(browseBackend.supports(), "应用内浏览应始终可用");
    }

    // 场景：initialPath 指向真实存在的目录时返回该目录的绝对路径
    @Test
    @DisplayName("Browse 对有效目录返回其绝对路径")
    void browseReturnsAbsolutePathForValidDirectory(@TempDir Path dir) {
        Optional<String> picked = browseBackend.pick("选择目录", dir.toString());

        assertTrue(picked.isPresent(), "有效目录应能被选中");
        assertEquals(dir.toAbsolutePath().toString(), picked.get(), "应返回目录的绝对路径");
    }

    // 场景：initialPath 为 null 时无法选择，返回 empty
    @Test
    @DisplayName("Browse 对 null 初始路径返回 empty")
    void browseReturnsEmptyForNullInitialPath() {
        assertTrue(browseBackend.pick("选择目录", null).isEmpty(), "无初始目录应返回 empty");
    }

    // 场景：initialPath 为空白字符串时视为未提供，返回 empty
    @Test
    @DisplayName("Browse 对空白初始路径返回 empty")
    void browseReturnsEmptyForBlankInitialPath() {
        assertTrue(browseBackend.pick("选择目录", "   ").isEmpty(), "空白目录应返回 empty");
    }

    // 场景：initialPath 指向不存在的目录时返回 empty
    @Test
    @DisplayName("Browse 对不存在的目录返回 empty")
    void browseReturnsEmptyForMissingDirectory(@TempDir Path dir) {
        String missing = dir.resolve("definitely-missing-sub-dir").toString();

        assertTrue(browseBackend.pick("选择目录", missing).isEmpty(), "不存在的目录应返回 empty");
    }

    // ================= AutoDirectoryPickerBackend =================

    // 场景：只要任一后端支持，auto 后端即声明支持（此处仅 browse 支持）
    @Test
    @DisplayName("Auto 在 browse 可用时声明支持")
    void autoSupportsWhenBrowseSupports() {
        AutoDirectoryPickerBackend auto = new AutoDirectoryPickerBackend(nativeStub(false, null), browseBackend);

        assertTrue(auto.supports(), "browse 可用时 auto 应支持");
    }

    // 场景：仅 native 支持时 auto 也应声明支持
    @Test
    @DisplayName("Auto 在仅 native 可用时声明支持")
    void autoSupportsWhenOnlyNativeSupports() {
        AutoDirectoryPickerBackend auto =
                new AutoDirectoryPickerBackend(nativeStub(true, "/native/only"), unavailableBrowseStub());

        assertTrue(auto.supports(), "native 可用时 auto 应支持");
    }

    // 场景：两个后端都不可用时 auto 声明不支持，且 pick 返回 empty
    @Test
    @DisplayName("Auto 在两者都不可用时返回 empty")
    void autoReturnsEmptyWhenBothUnsupported() {
        AutoDirectoryPickerBackend auto =
                new AutoDirectoryPickerBackend(nativeStub(false, null), unavailableBrowseStub());

        assertFalse(auto.supports(), "两个后端都不可用时 auto 应不支持");
        assertTrue(auto.pick("选择目录", "/tmp").isEmpty(), "都不可用时 pick 应返回 empty");
    }

    // 场景：native 可用且成功返回时，auto 优先采用 native 结果
    @Test
    @DisplayName("Auto 优先返回 native 的结果")
    void autoPrefersNativeResult(@TempDir Path dir) {
        AutoDirectoryPickerBackend auto =
                new AutoDirectoryPickerBackend(nativeStub(true, "/native/picked"), browseBackend);

        Optional<String> picked = auto.pick("选择目录", dir.toString());

        assertTrue(picked.isPresent(), "native 成功时应返回结果");
        assertEquals("/native/picked", picked.get(), "应优先采用 native 的返回值");
    }

    // 场景：native 声明可用但未选中（empty）时，auto 回退到 browse 并返回初始目录
    @Test
    @DisplayName("Auto 在 native 未选中时回退 browse")
    void autoFallsBackToBrowseWhenNativeReturnsEmpty(@TempDir Path dir) {
        AutoDirectoryPickerBackend auto = new AutoDirectoryPickerBackend(nativeStub(true, null), browseBackend);

        Optional<String> picked = auto.pick("选择目录", dir.toString());

        assertTrue(picked.isPresent(), "native 未选中时应回退 browse 成功");
        assertEquals(dir.toAbsolutePath().toString(), picked.get(), "回退后应返回 browse 的绝对路径");
    }

    // 场景：native 不可用时，auto 直接回退到 browse
    @Test
    @DisplayName("Auto 在 native 不可用时回退 browse")
    void autoFallsBackToBrowseWhenNativeUnsupported(@TempDir Path dir) {
        AutoDirectoryPickerBackend auto = new AutoDirectoryPickerBackend(nativeStub(false, null), browseBackend);

        Optional<String> picked = auto.pick("选择目录", dir.toString());

        assertTrue(picked.isPresent(), "native 不可用时应回退 browse 成功");
        assertEquals(dir.toAbsolutePath().toString(), picked.get(), "应返回 browse 的绝对路径");
    }

    // 场景：native 未选中且 browse 无法选择（无初始目录）时返回 empty
    @Test
    @DisplayName("Auto 在 native 未选中且 browse 无法选择时返回 empty")
    void autoReturnsEmptyWhenNativeEmptyAndBrowseCannotPick() {
        AutoDirectoryPickerBackend auto = new AutoDirectoryPickerBackend(nativeStub(true, null), browseBackend);

        assertTrue(auto.pick("选择目录", null).isEmpty(), "browse 无初始目录时应返回 empty");
    }
}