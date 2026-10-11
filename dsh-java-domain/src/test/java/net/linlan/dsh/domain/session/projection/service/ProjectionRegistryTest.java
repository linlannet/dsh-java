package net.linlan.dsh.domain.session.projection.service;

import net.linlan.dsh.domain.session.event.model.entity.SessionEvent;
import net.linlan.dsh.domain.session.event.service.SessionEventFactory;
import net.linlan.dsh.domain.session.projection.adapter.port.ProjectionProvider;
import net.linlan.dsh.domain.session.projection.model.valobj.ProjectionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ProjectionRegistry 投影框架路由")
class ProjectionRegistryTest {

    @Test
    @DisplayName("注册 provider 后按类型路由可解析")
    void registerAndResolve() {
        var registry = new ProjectionRegistry();
        var provider = new TestProvider(ProjectionType.SURFACE);
        registry.register(provider);

        var resolved = registry.resolve(ProjectionType.SURFACE);
        assertTrue(resolved.isPresent());
        assertSame(provider, resolved.get());
    }

    @Test
    @DisplayName("未注册类型 resolve 返回 Optional.empty()")
    void unregisteredTypeReturnsEmpty() {
        var registry = new ProjectionRegistry();

        var resolved = registry.resolve(ProjectionType.STATISTICS);
        assertTrue(resolved.isEmpty());
    }

    @Test
    @DisplayName("注册多个 provider 各自路由正确")
    void multipleProviders() {
        var registry = new ProjectionRegistry();
        var surface = new TestProvider(ProjectionType.SURFACE);
        var stats = new TestProvider(ProjectionType.STATISTICS);
        var outline = new TestProvider(ProjectionType.TURN_OUTLINE);
        registry.register(surface);
        registry.register(stats);
        registry.register(outline);

        assertEquals(3, registry.size());
        assertSame(surface, registry.resolve(ProjectionType.SURFACE).orElseThrow());
        assertSame(stats, registry.resolve(ProjectionType.STATISTICS).orElseThrow());
        assertSame(outline, registry.resolve(ProjectionType.TURN_OUTLINE).orElseThrow());
    }

    @Test
    @DisplayName("重复注册同类型覆盖旧 provider")
    void overrideRegistration() {
        var registry = new ProjectionRegistry();
        var first = new TestProvider(ProjectionType.SURFACE);
        var second = new TestProvider(ProjectionType.SURFACE);
        registry.register(first);
        registry.register(second);

        assertSame(second, registry.resolve(ProjectionType.SURFACE).orElseThrow());
        assertEquals(1, registry.size());
    }

    @Test
    @DisplayName("ProjectionType 预定义常量正确")
    void predefinedTypes() {
        assertEquals("SURFACE", ProjectionType.SURFACE.name());
        assertEquals("STATISTICS", ProjectionType.STATISTICS.name());
        assertEquals("TURN_OUTLINE", ProjectionType.TURN_OUTLINE.name());
    }

    @Test
    @DisplayName("ProjectionType 空名抛异常")
    void emptyTypeNameRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ProjectionType(""));
    }

    private static final class TestProvider implements ProjectionProvider<List<String>> {
        private final ProjectionType type;

        TestProvider(ProjectionType type) { this.type = type; }

        @Override public ProjectionType type() { return type; }
        @Override public Class<List<String>> projectionClass() { return (Class) List.class; }
        @Override public List<String> initial() { return List.of(); }
        @Override public List<String> applyIncremental(List<String> current, SessionEvent event) {
            return current;
        }
    }
}
