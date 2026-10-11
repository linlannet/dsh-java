package net.linlan.dsh.domain.core.scope;

import net.linlan.dsh.domain.core.scope.model.valobj.ScopeKey;
import net.linlan.dsh.domain.core.scope.model.valobj.ScopedEvent;
import net.linlan.dsh.domain.core.scope.service.ScopeEventDispatcher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ScopeEventDispatcher — 作用域过滤事件分发")
class ScopeEventDispatcherTest {

    // ── ScopeKey 值对象 ──────────────────────────────────────

    @Test
    @DisplayName("根作用域相等性")
    void rootEquals() {
        assertEquals(ScopeKey.root("session"), ScopeKey.root("session"));
        assertNotEquals(ScopeKey.root("session"), ScopeKey.root("tool"));
    }

    @Test
    @DisplayName("子作用域是父作用域的严格后代")
    void childIsDescendantOfParent() {
        var root = ScopeKey.root("session");
        var child = ScopeKey.child(root, "tool");
        assertTrue(child.isDescendantOf(root));
        assertFalse(root.isDescendantOf(child));
    }

    @Test
    @DisplayName("多层嵌套后代关系")
    void nestedDescendant() {
        var session = ScopeKey.root("session");
        var turn = ScopeKey.child(session, "turn-1");
        var step = ScopeKey.child(turn, "step-1");
        assertTrue(step.isDescendantOf(session));
        assertTrue(step.isDescendantOf(turn));
        assertFalse(turn.isDescendantOf(step));
    }

    @Test
    @DisplayName("isDescendantOfOrEquals 包含自身")
    void descendantOrEqualsIncludesSelf() {
        var root = ScopeKey.root("session");
        assertTrue(root.isDescendantOfOrEquals(root));
    }

    @Test
    @DisplayName("根作用域 parent 返回 null")
    void rootParentIsNull() {
        assertNull(ScopeKey.root("session").parent());
    }

    @Test
    @DisplayName("子作用域 parent 返回父作用域")
    void childParent() {
        var root = ScopeKey.root("session");
        var child = ScopeKey.child(root, "turn-1");
        assertEquals(root, child.parent());
    }

    @Test
    @DisplayName("不同结构不相等")
    void differentStructureNotEqual() {
        var a = ScopeKey.child(ScopeKey.root("session"), "turn-1");
        var b = ScopeKey.child(ScopeKey.root("session"), "turn-2");
        assertNotEquals(a, b);
    }

    @Test
    @DisplayName("空名称抛异常")
    void emptyNameThrows() {
        assertThrows(IllegalArgumentException.class, () -> ScopeKey.root(""));
        assertThrows(IllegalArgumentException.class, () -> ScopeKey.root(null));
    }

    // ── 事件分发 ─────────────────────────────────────────────

    @Test
    @DisplayName("事件在订阅 scope 发布，订阅者收到")
    void sameScopeReceived() {
        var dispatcher = new ScopeEventDispatcher();
        var scope = ScopeKey.root("session");
        List<String> received = new ArrayList<>();

        dispatcher.subscribe(scope, e -> received.add(e.type()));
        dispatcher.dispatch(ScopedEvent.of(scope, "session/event", "payload"));

        assertEquals(List.of("session/event"), received);
    }

    @Test
    @DisplayName("事件在子 scope 发布，父 scope 订阅者收到")
    void childScopeEventReceivedByParentSubscriber() {
        var dispatcher = new ScopeEventDispatcher();
        var session = ScopeKey.root("session");
        var tool = ScopeKey.child(session, "tool");
        List<String> received = new ArrayList<>();

        dispatcher.subscribe(session, e -> received.add(e.type()));
        dispatcher.dispatch(ScopedEvent.of(tool, "tool/call", "payload"));

        assertEquals(List.of("tool/call"), received);
    }

    @Test
    @DisplayName("事件在父 scope 发布，子 scope 订阅者不收到")
    void parentScopeEventNotReceivedByChildSubscriber() {
        var dispatcher = new ScopeEventDispatcher();
        var session = ScopeKey.root("session");
        var tool = ScopeKey.child(session, "tool");
        List<String> received = new ArrayList<>();

        dispatcher.subscribe(tool, e -> received.add(e.type()));
        dispatcher.dispatch(ScopedEvent.of(session, "session/event", "payload"));

        assertTrue(received.isEmpty());
    }

    @Test
    @DisplayName("不相关 scope 的订阅者不收到")
    void unrelatedScopeNotReceived() {
        var dispatcher = new ScopeEventDispatcher();
        var session = ScopeKey.root("session");
        var other = ScopeKey.root("other");
        List<String> received = new ArrayList<>();

        dispatcher.subscribe(other, e -> received.add(e.type()));
        dispatcher.dispatch(ScopedEvent.of(session, "session/event", "payload"));

        assertTrue(received.isEmpty());
    }

    @Test
    @DisplayName("取消订阅后不再收到")
    void unsubscribeStopsDelivery() {
        var dispatcher = new ScopeEventDispatcher();
        var scope = ScopeKey.root("session");
        List<String> received = new ArrayList<>();

        var token = dispatcher.subscribe(scope, e -> received.add(e.type()));
        dispatcher.dispatch(ScopedEvent.of(scope, "event-1", "p1"));
        dispatcher.unsubscribe(token);
        dispatcher.dispatch(ScopedEvent.of(scope, "event-2", "p2"));

        assertEquals(List.of("event-1"), received);
    }

    @Test
    @DisplayName("多个订阅者各自收到匹配事件")
    void multipleSubscribers() {
        var dispatcher = new ScopeEventDispatcher();
        var session = ScopeKey.root("session");
        var tool = ScopeKey.child(session, "tool");
        List<String> sessionReceived = new ArrayList<>();
        List<String> toolReceived = new ArrayList<>();

        dispatcher.subscribe(session, e -> sessionReceived.add(e.type()));
        dispatcher.subscribe(tool, e -> toolReceived.add(e.type()));

        dispatcher.dispatch(ScopedEvent.of(tool, "tool/call", "p"));

        assertEquals(List.of("tool/call"), sessionReceived);
        assertEquals(List.of("tool/call"), toolReceived);
    }

    @Test
    @DisplayName("handler 异常不中断其他订阅者")
    void handlerExceptionDoesNotInterruptOthers() {
        var dispatcher = new ScopeEventDispatcher();
        var scope = ScopeKey.root("session");
        List<String> received = new ArrayList<>();

        dispatcher.subscribe(scope, e -> { throw new RuntimeException("boom"); });
        dispatcher.subscribe(scope, e -> received.add(e.type()));
        dispatcher.dispatch(ScopedEvent.of(scope, "event", "p"));

        assertEquals(List.of("event"), received);
    }

    // ── 线程安全 ─────────────────────────────────────────────

    @Test
    @DisplayName("并发 dispatch/subscribe 不抛异常")
    void concurrentDispatchAndSubscribe() throws InterruptedException {
        var dispatcher = new ScopeEventDispatcher();
        var scope = ScopeKey.root("session");
        int threads = 10;
        int opsPerThread = 100;
        var latch = new CountDownLatch(threads);

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    try {
                        for (int j = 0; j < opsPerThread; j++) {
                            var token = dispatcher.subscribe(scope, e -> {});
                            dispatcher.dispatch(ScopedEvent.of(scope, "event", j));
                            if (j % 2 == 0) {
                                dispatcher.unsubscribe(token);
                            }
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await();
        } finally {
            pool.shutdown();
        }

        assertTrue(dispatcher.activeSubscriptionCount() > 0);
    }
}
