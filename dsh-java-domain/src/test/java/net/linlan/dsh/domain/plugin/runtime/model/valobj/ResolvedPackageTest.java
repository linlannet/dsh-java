package net.linlan.dsh.domain.plugin.runtime.model.valobj;

import net.linlan.dsh.domain.plugin.runtime.model.valobj.ResolvedPackage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("T1.034 ResolvedPackage 值对象")
class ResolvedPackageTest {

    @Test
    @DisplayName("ResolvedPackage 不可变 record")
    void immutableRecord() {
        var pkg = new ResolvedPackage("mod-1", "com.example.Main", "/path/to/jar", "JAVA_NATIVE", Instant.now());

        assertEquals("mod-1", pkg.moduleId());
        assertEquals("com.example.Main", pkg.entryPoint());
        assertEquals("/path/to/jar", pkg.sourcePath());
        assertEquals("JAVA_NATIVE", pkg.packageType());
        assertNotNull(pkg.resolvedAt());
    }

    @Test
    @DisplayName("null 字段归一化")
    void nullFieldsNormalized() {
        var pkg = new ResolvedPackage("mod-1", null, null, null, null);

        assertEquals("mod-1", pkg.moduleId());
        assertEquals("", pkg.entryPoint());
        assertEquals("", pkg.sourcePath());
        assertEquals("unknown", pkg.packageType());
        assertNotNull(pkg.resolvedAt());
    }

    @Test
    @DisplayName("moduleId 为 null 抛异常")
    void nullModuleIdThrows() {
        assertThrows(NullPointerException.class, () -> new ResolvedPackage(null, "", "", "", Instant.now()));
    }
}
