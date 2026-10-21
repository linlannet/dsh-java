package net.linlan.dsh.infrastructure.adapter.typert;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * CpythonPtcAdapterTest CPython PTC 运行时适配器单元测试。
 *
 * <p>入参校验与平台能力判定为纯逻辑，可直接断言；真实进程执行依赖宿主 CPython，
 * 因此在非 macOS/Linux 或未安装 python3 的环境使用 JUnit Assumptions 跳过。</p>
 */
@DisplayName("CpythonPtcAdapter CPython PTC 运行时适配器")
public class CpythonPtcAdapterTest {

    /** 探测宿主是否可在支持平台上真实执行 python3。 */
    private boolean python3Available() {
        try {
            new CpythonPtcAdapter().execute("print('probe')", null);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    // 场景：script 为 null 时应抛出 IllegalArgumentException 并指明缺少脚本
    @Test
    @DisplayName("script 为 null 抛出 IllegalArgumentException")
    void nullScriptThrowsIllegalArgumentException() {
        CpythonPtcAdapter adapter = new CpythonPtcAdapter();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> adapter.execute(null, null), "null 脚本应被拒绝");
        assertEquals("script required", ex.getMessage(), "异常信息应指出脚本缺失");
    }

    // 场景：script 为空白时应抛出 IllegalArgumentException
    @Test
    @DisplayName("script 为空白抛出 IllegalArgumentException")
    void blankScriptThrowsIllegalArgumentException() {
        CpythonPtcAdapter adapter = new CpythonPtcAdapter();

        assertThrows(IllegalArgumentException.class, () -> adapter.execute("   ", Map.of()),
                "空白脚本应被拒绝");
    }

    // 场景：platformSupported 判定规则应与宿主 os.name 是否属于 macOS/Linux 一致
    @Test
    @DisplayName("platformSupported 与 os.name 判定一致")
    void platformSupportedMatchesOsName() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        boolean expected = os.contains("mac") || os.contains("linux") || os.contains("nux") || os.contains("nix");

        assertEquals(expected, new CpythonPtcAdapter().platformSupported(),
                "平台支持判定应与 os.name 规则一致，当前 os.name=" + os);
    }

    // 场景：不存在的 python 可执行文件在支持平台上应抛 IllegalStateException（运行时不可用）
    @Test
    @DisplayName("不存在的解释器抛出 IllegalStateException")
    void missingExecutableThrowsIllegalStateException() {
        assumeTrue(new CpythonPtcAdapter().platformSupported(), "非 macOS/Linux 平台跳过");

        CpythonPtcAdapter adapter = new CpythonPtcAdapter("/definitely/not/exists/python-xyz");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> adapter.execute("print(1)", null), "解释器缺失应报告运行时不可用");
        assertEquals("CPython runtime unavailable", ex.getMessage(), "异常信息应指明 CPython 不可用");
    }

    // 场景：默认构造使用 python3，可真实执行简单脚本并返回其标准输出
    @Test
    @DisplayName("默认构造用 python3 执行简单脚本")
    void defaultConstructorExecutesSimpleScript() {
        assumeTrue(new CpythonPtcAdapter().platformSupported(), "非 macOS/Linux 平台跳过");
        assumeTrue(python3Available(), "宿主未安装可用 python3，跳过");

        String output = new CpythonPtcAdapter().execute("print(\"hello\")", null);

        assertTrue(output.contains("hello"), "输出应包含脚本打印内容，实际为: " + output);
    }

    // 场景：多行脚本的每一行输出都应按换行拼接返回
    @Test
    @DisplayName("多行输出按换行拼接")
    void multiLineOutputIsJoined() {
        assumeTrue(new CpythonPtcAdapter().platformSupported(), "非 macOS/Linux 平台跳过");
        assumeTrue(python3Available(), "宿主未安装可用 python3，跳过");

        String output = new CpythonPtcAdapter().execute("print(1)\nprint(2)", null);

        assertEquals("1\n2", output, "多行输出应以换行拼接");
    }

    // 场景：env 中的条目应注入子进程环境变量并可被脚本读取
    @Test
    @DisplayName("env 注入子进程环境变量")
    void envIsInjectedIntoSubprocess() {
        assumeTrue(new CpythonPtcAdapter().platformSupported(), "非 macOS/Linux 平台跳过");
        assumeTrue(python3Available(), "宿主未安装可用 python3，跳过");

        String output = new CpythonPtcAdapter()
                .execute("import os\nprint(os.environ.get('DSH_UT_ENV', ''))", Map.of("DSH_UT_ENV", "env-value"));

        assertEquals("env-value", output, "脚本应读到注入的环境变量");
    }

    // 场景：env 为 null 时不应报错，脚本仍可正常执行
    @Test
    @DisplayName("env 为 null 时仍可执行")
    void nullEnvStillExecutes() {
        assumeTrue(new CpythonPtcAdapter().platformSupported(), "非 macOS/Linux 平台跳过");
        assumeTrue(python3Available(), "宿主未安装可用 python3，跳过");

        String output = new CpythonPtcAdapter().execute("print('ok')", null);

        assertEquals("ok", output, "env 为 null 时脚本应正常执行");
    }

    // 场景：构造参数为空白时应回退到 python3 并能正常执行
    @Test
    @DisplayName("空白解释器路径回退为 python3")
    void blankExecutableFallsBackToPython3() {
        assumeTrue(new CpythonPtcAdapter().platformSupported(), "非 macOS/Linux 平台跳过");
        assumeTrue(python3Available(), "宿主未安装可用 python3，跳过");

        String output = new CpythonPtcAdapter("   ").execute("print(\"fallback\")", null);

        assertTrue(output.contains("fallback"), "回退 python3 后应正常执行，实际为: " + output);
    }

    // 场景：非 macOS/Linux 平台应拒绝执行（临时改写 os.name 模拟 Windows 后恢复）
    @Test
    @DisplayName("非支持平台抛出 IllegalStateException")
    void unsupportedPlatformThrowsIllegalStateException() {
        String originalOs = System.getProperty("os.name");
        try {
            System.setProperty("os.name", "Windows 11");
            CpythonPtcAdapter adapter = new CpythonPtcAdapter();

            assertFalse(adapter.platformSupported(), "Windows 应判定为平台不支持");

            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> adapter.execute("print(1)", null), "不支持平台应拒绝执行");
            assertEquals("CPython PTC runtime unavailable on this platform", ex.getMessage(),
                    "异常信息应指明平台不支持");
        } finally {
            // 恢复宿主 os.name，避免污染其他测试
            if (originalOs == null) {
                System.clearProperty("os.name");
            } else {
                System.setProperty("os.name", originalOs);
            }
        }
    }
}