package net.linlan.dsh.domain.tool.fs;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FsObservationPolicy 文件系统观察策略")
class FsObservationPolicyTest {

    @Test
    @DisplayName("register 后 notify 触发回调")
    void registerThenNotifyInvokesCallback() {
        var policy = new FsObservationPolicy();
        List<String> events = new ArrayList<>();
        policy.register("/watch", "*", events::add);
        policy.notify("/watch", "modify");
        assertEquals(1, events.size());
        assertEquals("modify", events.get(0));
    }

    @Test
    @DisplayName("unregister 后 notify 不触发回调")
    void unregisterStopsNotification() {
        var policy = new FsObservationPolicy();
        List<String> events = new ArrayList<>();
        policy.register("/watch", "*", events::add);
        policy.unregister("/watch");
        policy.notify("/watch", "modify");
        assertTrue(events.isEmpty());
    }

    @Test
    @DisplayName("未注册路径 notify 无效果")
    void notifyUnregisteredPathNoOp() {
        var policy = new FsObservationPolicy();
        policy.notify("/unknown", "modify");
        assertEquals(0, policy.registrationCount());
    }

    @Test
    @DisplayName("null path 注册抛出异常")
    void registerNullPathThrows() {
        var policy = new FsObservationPolicy();
        assertThrows(IllegalArgumentException.class, () -> policy.register(null, "*", s -> {}));
    }

    @Test
    @DisplayName("blank path 注册抛出异常")
    void registerBlankPathThrows() {
        var policy = new FsObservationPolicy();
        assertThrows(IllegalArgumentException.class, () -> policy.register("  ", "*", s -> {}));
    }

    @Test
    @DisplayName("null callback 注册抛出异常")
    void registerNullCallbackThrows() {
        var policy = new FsObservationPolicy();
        assertThrows(IllegalArgumentException.class, () -> policy.register("/watch", "*", null));
    }

    @Test
    @DisplayName("null pattern 视为通配符 *")
    void nullPatternActsAsWildcard() {
        var policy = new FsObservationPolicy();
        List<String> events = new ArrayList<>();
        policy.register("/watch", null, events::add);
        policy.notify("/watch", "create");
        assertEquals(1, events.size());
    }

    @Test
    @DisplayName("notify null path 无效果")
    void notifyNullPathNoOp() {
        var policy = new FsObservationPolicy();
        policy.register("/watch", "*", s -> {});
        policy.notify(null, "modify");
    }

    @Test
    @DisplayName("notify null kind 视为 change")
    void notifyNullKindDefaultsToChange() {
        var policy = new FsObservationPolicy();
        List<String> events = new ArrayList<>();
        policy.register("/watch", "*", events::add);
        policy.notify("/watch", null);
        assertEquals("change", events.get(0));
    }

    @Test
    @DisplayName("isRegistered 反映注册状态")
    void isRegisteredReflectsState() {
        var policy = new FsObservationPolicy();
        policy.register("/a", "*", s -> {});
        assertTrue(policy.isRegistered("/a"));
        assertFalse(policy.isRegistered("/b"));
        policy.unregister("/a");
        assertFalse(policy.isRegistered("/a"));
    }

    @Test
    @DisplayName("registrationCount 返回注册数量")
    void registrationCountTracksRegistrations() {
        var policy = new FsObservationPolicy();
        assertEquals(0, policy.registrationCount());
        policy.register("/a", "*", s -> {});
        policy.register("/b", "*.java", s -> {});
        assertEquals(2, policy.registrationCount());
    }

    @Test
    @DisplayName("重新注册同路径覆盖旧回调")
    void reRegisterOverwritesPrevious() {
        var policy = new FsObservationPolicy();
        List<String> events1 = new ArrayList<>();
        List<String> events2 = new ArrayList<>();
        policy.register("/watch", "*", events1::add);
        policy.register("/watch", "*", events2::add);
        policy.notify("/watch", "modify");
        assertTrue(events1.isEmpty());
        assertEquals(1, events2.size());
    }
}
