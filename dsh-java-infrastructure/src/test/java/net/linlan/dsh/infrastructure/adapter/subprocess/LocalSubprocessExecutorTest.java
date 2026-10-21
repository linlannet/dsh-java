package net.linlan.dsh.infrastructure.adapter.subprocess;

import net.linlan.dsh.domain.subprocess.model.valobj.SubprocessResult;
import net.linlan.dsh.domain.subprocess.model.valobj.SubprocessSpec;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LocalSubprocessExecutorTest 本地子进程执行适配器单元测试。
 *
 * <p>只使用系统自带的基础命令（绝对路径调用，避免依赖 PATH），覆盖退出码、
 * 标准输出/错误捕获、工作目录与显式环境变量传递，以及 collect 与 execute 的一致性。</p>
 */
@DisplayName("LocalSubprocessExecutor 本地子进程执行适配器")
public class LocalSubprocessExecutorTest {

    private final LocalSubprocessExecutor executor = new LocalSubprocessExecutor();

    private final String echoBin = firstExisting("/bin/echo", "/usr/bin/echo");
    private final String falseBin = firstExisting("/usr/bin/false", "/bin/false");
    private final String pwdBin = firstExisting("/bin/pwd", "/usr/bin/pwd");
    private final String shBin = firstExisting("/bin/sh", "/usr/bin/sh");
    private final String envBin = firstExisting("/usr/bin/env", "/bin/env");

    private static String firstExisting(String... candidates) {
        for (String candidate : candidates) {
            if (Files.isExecutable(Path.of(candidate))) {
                return candidate;
            }
        }
        return candidates[0];
    }

    // 场景：echo 命令正常退出，exitCode 为 0 且标准输出包含传入的内容
    @Test
    @DisplayName("echo 成功退出且捕获标准输出")
    void echoCommandSucceeds() {
        SubprocessResult result = executor.execute(SubprocessSpec.of(List.of(echoBin, "hello-subprocess")));

        assertEquals(0, result.exitCode(), "echo 应正常退出");
        assertTrue(result.success(), "退出码为 0 应判定成功");
        assertFalse(result.spilled(), "小输出不应 spill");
        assertTrue(result.stdout().contains("hello-subprocess"),
                "标准输出应包含参数内容，实际为: " + result.stdout());
        assertEquals("", result.stderr(), "echo 不应产生标准错误");
    }

    // 场景：false 命令以非 0 退出，success 应为 false
    @Test
    @DisplayName("false 命令返回非 0 退出码")
    void falseCommandReturnsNonZeroExitCode() {
        SubprocessResult result = executor.execute(SubprocessSpec.of(List.of(falseBin)));

        assertNotEquals(0, result.exitCode(), "false 应返回非 0 退出码");
        assertFalse(result.success(), "非 0 退出码不应判定成功");
    }

    // 场景：可执行文件不存在时捕获启动异常，返回 exitCode -1 且错误信息带入 stderr
    @Test
    @DisplayName("可执行文件不存在返回 -1")
    void missingExecutableReturnsMinusOne() {
        SubprocessResult result = executor.execute(SubprocessSpec.of(List.of("/no/such/binary-xyz")));

        assertEquals(-1, result.exitCode(), "启动失败应返回 -1");
        assertFalse(result.success(), "启动失败不应判定成功");
        assertFalse(result.stderr().isEmpty(), "应把启动异常信息写入 stderr");
    }

    // 场景：显式指定的 cwd 应作为子进程工作目录生效
    @Test
    @DisplayName("cwd 作为子进程工作目录生效")
    void cwdIsApplied(@TempDir Path dir) throws Exception {
        SubprocessSpec spec = new SubprocessSpec(List.of(pwdBin), dir.toString(), "pipe", Map.of(), 5000, "SIGTERM");

        SubprocessResult result = executor.execute(spec);

        assertEquals(0, result.exitCode(), "pwd 应正常退出");
        Path reported = Path.of(result.stdout().trim());
        assertEquals(dir.toRealPath(), reported.toRealPath(), "子进程工作目录应为指定目录");
    }

    // 场景：envOverride 中的变量应传递给子进程并可被其读取
    @Test
    @DisplayName("envOverride 传递给子进程")
    void envOverrideIsPassedToChild() {
        SubprocessSpec spec = new SubprocessSpec(
                List.of(shBin, "-c", "printf %s \"$DSH_UT_VAR\""),
                null, "pipe", Map.of("DSH_UT_VAR", "env-value"), 5000, null);

        SubprocessResult result = executor.execute(spec);

        assertEquals(0, result.exitCode(), "sh 应正常退出");
        // 执行器按行追加换行，比较前先去除行尾换行
        assertEquals("env-value", result.stdout().trim(), "子进程应读到注入的变量值");
    }

    // 场景：子进程环境以 envOverride 为准，不继承宿主环境（避免隐式依赖）
    @Test
    @DisplayName("子进程环境以 envOverride 为准")
    void childEnvironmentUsesOverrideOnly() {
        SubprocessSpec spec = new SubprocessSpec(
                List.of(envBin), null, "pipe", Map.of("ONLY_VAR", "1"), 5000, null);

        SubprocessResult result = executor.execute(spec);

        assertEquals(0, result.exitCode(), "env 应正常退出");
        assertTrue(result.stdout().contains("ONLY_VAR=1"), "应包含显式注入的变量，实际为: " + result.stdout());
        assertFalse(result.stdout().contains("PATH="), "宿主 PATH 不应被继承");
    }

    // 场景：子进程写入标准错误的输出应被单独捕获
    @Test
    @DisplayName("标准错误被单独捕获")
    void stderrIsCaptured() {
        SubprocessSpec spec = new SubprocessSpec(
                List.of(shBin, "-c", "printf %s \"oops\" 1>&2"), null, "pipe", Map.of(), 5000, null);

        SubprocessResult result = executor.execute(spec);

        assertEquals(0, result.exitCode(), "sh 应正常退出");
        assertEquals("oops", result.stderr().trim(), "标准错误应被捕获");
        assertTrue(result.stdout().trim().isEmpty(), "标准输出应为空");
    }

    // 场景：collect 与 execute 对同一 spec 的行为应完全一致
    @Test
    @DisplayName("collect 与 execute 行为一致")
    void collectBehavesLikeExecute() {
        SubprocessSpec spec = SubprocessSpec.of(List.of(echoBin, "collect-echo"));

        SubprocessResult viaExecute = executor.execute(spec);
        SubprocessResult viaCollect = executor.collect(spec, 5000L);

        assertEquals(viaExecute.exitCode(), viaCollect.exitCode(), "退出码应一致");
        assertEquals(viaExecute.stdout(), viaCollect.stdout(), "标准输出应一致");
        assertEquals(viaExecute.stderr(), viaCollect.stderr(), "标准错误应一致");
        assertTrue(viaCollect.success(), "collect 结果应判定成功");
    }
}