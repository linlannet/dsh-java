package net.linlan.dsh.domain.guard.timeout;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PerToolDeadlinePolicyLoader 配置加载")
class PerToolDeadlinePolicyLoaderTest {

    @Test
    @DisplayName("fromProperties 解析工具超时配置")
    void fromPropertiesParsesToolDeadlines() {
        Properties props = new Properties();
        props.setProperty("write_file", "10s");
        props.setProperty("read_file", "5s");
        props.setProperty("default", "30s");

        var policy = PerToolDeadlinePolicyLoader.fromProperties(props);

        assertEquals(Duration.ofSeconds(10), policy.deadline("write_file"));
        assertEquals(Duration.ofSeconds(5), policy.deadline("read_file"));
        assertEquals(Duration.ofSeconds(30), policy.defaultDeadline());
    }

    @Test
    @DisplayName("未配置的工具使用默认超时")
    void unconfiguredToolUsesDefault() {
        Properties props = new Properties();
        props.setProperty("write_file", "10s");
        props.setProperty("default", "30s");

        var policy = PerToolDeadlinePolicyLoader.fromProperties(props);

        assertEquals(Duration.ofSeconds(30), policy.deadline("unknown_tool"));
    }

    @Test
    @DisplayName("无 default 配置时使用 30s 默认值")
    void noDefaultUses30s() {
        Properties props = new Properties();
        props.setProperty("write_file", "10s");

        var policy = PerToolDeadlinePolicyLoader.fromProperties(props);

        assertEquals(Duration.ofSeconds(30), policy.defaultDeadline());
    }

    @Test
    @DisplayName("fromMap 从 Map 构建策略")
    void fromMapBuildsPolicy() {
        var config = Map.of(
                "write_file", "10s",
                "bash", "60s",
                "default", "15s"
        );

        var policy = PerToolDeadlinePolicyLoader.fromMap(config);

        assertEquals(Duration.ofSeconds(10), policy.deadline("write_file"));
        assertEquals(Duration.ofSeconds(60), policy.deadline("bash"));
        assertEquals(Duration.ofSeconds(15), policy.defaultDeadline());
    }

    @Test
    @DisplayName("parseDuration 支持多种时间单位")
    void parseDurationSupportsUnits() {
        assertEquals(Duration.ofMillis(500), PerToolDeadlinePolicyLoader.parseDuration("500ms"));
        assertEquals(Duration.ofSeconds(30), PerToolDeadlinePolicyLoader.parseDuration("30s"));
        assertEquals(Duration.ofMinutes(5), PerToolDeadlinePolicyLoader.parseDuration("5m"));
        assertEquals(Duration.ofHours(1), PerToolDeadlinePolicyLoader.parseDuration("1h"));
        assertEquals(Duration.ofSeconds(45), PerToolDeadlinePolicyLoader.parseDuration("45"));
    }

    @Test
    @DisplayName("parseDuration 支持空格和大小写")
    void parseDurationTolerantFormat() {
        assertEquals(Duration.ofSeconds(10), PerToolDeadlinePolicyLoader.parseDuration(" 10s "));
        assertEquals(Duration.ofSeconds(10), PerToolDeadlinePolicyLoader.parseDuration("10S"));
    }

    @Test
    @DisplayName("空 Properties 返回默认策略")
    void emptyPropertiesReturnsDefaultPolicy() {
        var policy = PerToolDeadlinePolicyLoader.fromProperties(new Properties());

        assertEquals(Duration.ofSeconds(30), policy.defaultDeadline());
        assertEquals(Duration.ofSeconds(30), policy.deadline("any_tool"));
    }
}
