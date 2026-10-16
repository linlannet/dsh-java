package net.linlan.dsh.domain.typert.ptc.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PtcSecurityValidator PTC 沙箱安全验证")
class PtcSecurityValidatorTest {

    @Test
    @DisplayName("默认策略接受正常脚本")
    void defaultPolicyAcceptsNormalScript() {
        var validator = new PtcSecurityValidator();
        var result = validator.validateScript("console.log('hello')");
        assertTrue(result.valid());
    }

    @Test
    @DisplayName("空脚本被拒绝")
    void emptyScriptRejected() {
        var validator = new PtcSecurityValidator();
        var result = validator.validateScript("");
        assertFalse(result.valid());
        assertTrue(result.reason().contains("empty"));
    }

    @Test
    @DisplayName("null 脚本被拒绝")
    void nullScriptRejected() {
        var validator = new PtcSecurityValidator();
        var result = validator.validateScript(null);
        assertFalse(result.valid());
    }

    @Test
    @DisplayName("超长脚本被拒绝")
    void oversizedScriptRejected() {
        var validator = new PtcSecurityValidator();
        String script = "x".repeat(70000);
        var result = validator.validateScript(script);
        assertFalse(result.valid());
        assertTrue(result.reason().contains("max length"));
    }

    @Test
    @DisplayName("child_process 模式被拦截")
    void childProcessPatternBlocked() {
        var validator = new PtcSecurityValidator();
        var result = validator.validateScript("require('child_process').execSync('rm -rf /')");
        assertFalse(result.valid());
        assertTrue(result.reason().contains("blocked pattern"));
    }

    @Test
    @DisplayName("eval 模式被拦截")
    void evalPatternBlocked() {
        var validator = new PtcSecurityValidator();
        var result = validator.validateScript("eval('malicious code')");
        assertFalse(result.valid());
        assertTrue(result.reason().contains("blocked pattern"));
    }

    @Test
    @DisplayName("process.exit 模式被拦截")
    void processExitPatternBlocked() {
        var validator = new PtcSecurityValidator();
        var result = validator.validateScript("process.exit(0)");
        assertFalse(result.valid());
    }

    @Test
    @DisplayName("敏感环境变量被拦截")
    void sensitiveEnvVarBlocked() {
        var validator = new PtcSecurityValidator();
        var result = validator.validateEnv(Map.of("API_KEY", "secret123"));
        assertFalse(result.valid());
        assertTrue(result.reason().contains("blocked pattern"));
    }

    @Test
    @DisplayName("PASSWORD 环境变量被拦截")
    void passwordEnvVarBlocked() {
        var validator = new PtcSecurityValidator();
        var result = validator.validateEnv(Map.of("DB_PASSWORD", "pwd"));
        assertFalse(result.valid());
    }

    @Test
    @DisplayName("正常环境变量通过")
    void normalEnvVarPasses() {
        var validator = new PtcSecurityValidator();
        var result = validator.validateEnv(Map.of("PATH", "/usr/bin", "HOME", "/home/user"));
        assertTrue(result.valid());
    }

    @Test
    @DisplayName("null 环境变量通过")
    void nullEnvPasses() {
        var validator = new PtcSecurityValidator();
        var result = validator.validateEnv(null);
        assertTrue(result.valid());
    }

    @Test
    @DisplayName("组合验证：脚本通过但环境变量失败")
    void combinedValidationScriptPassesEnvFails() {
        var validator = new PtcSecurityValidator();
        var result = validator.validate("console.log('ok')", Map.of("SECRET", "value"));
        assertFalse(result.valid());
        assertTrue(result.reason().contains("blocked pattern"));
    }

    @Test
    @DisplayName("组合验证：脚本和环境变量都通过")
    void combinedValidationBothPass() {
        var validator = new PtcSecurityValidator();
        var result = validator.validate("console.log('ok')", Map.of("PATH", "/usr/bin"));
        assertTrue(result.valid());
    }

    @Test
    @DisplayName("宽松策略允许所有")
    void lenientPolicyAllowsAll() {
        var validator = new PtcSecurityValidator(PtcSecurityPolicy.lenient());
        var result = validator.validate("require('child_process').execSync('rm')", Map.of("SECRET", "x"));
        assertTrue(result.valid());
    }

    @Test
    @DisplayName("null 策略回退到默认")
    void nullPolicyFallsBackToDefault() {
        var validator = new PtcSecurityValidator(null);
        assertEquals(PtcSecurityPolicy.defaults(), validator.policy());
    }

    @Test
    @DisplayName("自定义策略生效")
    void customPolicyApplied() {
        var policy = new PtcSecurityPolicy(100, java.util.Set.of("FORBIDDEN"), java.util.Set.of("dangerous("));
        var validator = new PtcSecurityValidator(policy);
        assertFalse(validator.validateScript("x".repeat(101)).valid());
        assertFalse(validator.validateScript("dangerous(arg)").valid());
        assertFalse(validator.validateEnv(Map.of("FORBIDDEN_KEY", "v")).valid());
        assertTrue(validator.validateScript("ok").valid());
    }
}
