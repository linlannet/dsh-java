package net.linlan.dsh.domain.session.workspace.model.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("WorkspaceEntity 工作空间实体")
class WorkspaceEntityTest {

    @Test
    @DisplayName("record 正确存储字段")
    void recordStoresFields() {
        var ws = new WorkspaceEntity("ws-1", "/project", List.of("s1", "s2"));
        assertEquals("ws-1", ws.workspaceId());
        assertEquals("/project", ws.rootPath());
        assertEquals(List.of("s1", "s2"), ws.attachedSessionIds());
    }

    @Test
    @DisplayName("null attachedSessionIds 转为空列表")
    void nullSessionIdsBecomesEmptyList() {
        var ws = new WorkspaceEntity("ws-1", "/project", null);
        assertTrue(ws.attachedSessionIds().isEmpty());
    }

    @Test
    @DisplayName("of 工厂方法创建空会话列表的工作空间")
    void ofFactoryCreatesEmptySessions() {
        var ws = WorkspaceEntity.of("ws-1", "/project");
        assertEquals("ws-1", ws.workspaceId());
        assertEquals("/project", ws.rootPath());
        assertTrue(ws.attachedSessionIds().isEmpty());
    }

    @Test
    @DisplayName("of 工厂方法带会话列表")
    void ofFactoryWithSessions() {
        var ws = WorkspaceEntity.of("ws-1", "/project", List.of("s1"));
        assertEquals(List.of("s1"), ws.attachedSessionIds());
    }

    @Test
    @DisplayName("attachSession 添加新会话并返回新实例")
    void attachSessionReturnsNewInstance() {
        var ws = WorkspaceEntity.of("ws-1", "/project");
        var updated = ws.attachSession("s1");
        assertNotSame(ws, updated);
        assertEquals(List.of("s1"), updated.attachedSessionIds());
    }

    @Test
    @DisplayName("attachSession 已存在会话返回同一实例")
    void attachExistingSessionReturnsSameInstance() {
        var ws = WorkspaceEntity.of("ws-1", "/project", List.of("s1"));
        var updated = ws.attachSession("s1");
        assertEquals(ws, updated);
    }

    @Test
    @DisplayName("detachSession 移除会话并返回新实例")
    void detachSessionReturnsNewInstance() {
        var ws = WorkspaceEntity.of("ws-1", "/project", List.of("s1", "s2"));
        var updated = ws.detachSession("s1");
        assertEquals(List.of("s2"), updated.attachedSessionIds());
    }

    @Test
    @DisplayName("detachSession 不存在会话返回同一实例")
    void detachNonexistentSessionReturnsSameInstance() {
        var ws = WorkspaceEntity.of("ws-1", "/project", List.of("s1"));
        var updated = ws.detachSession("s2");
        assertEquals(ws, updated);
    }

    @Test
    @DisplayName("空 workspaceId 抛出 IllegalArgumentException")
    void blankWorkspaceIdThrows() {
        assertThrows(IllegalArgumentException.class, () -> new WorkspaceEntity("", "/path", List.of()));
        assertThrows(IllegalArgumentException.class, () -> new WorkspaceEntity("  ", "/path", List.of()));
    }

    @Test
    @DisplayName("空 rootPath 抛出 IllegalArgumentException")
    void blankRootPathThrows() {
        assertThrows(IllegalArgumentException.class, () -> new WorkspaceEntity("ws-1", "", List.of()));
        assertThrows(IllegalArgumentException.class, () -> new WorkspaceEntity("ws-1", "  ", List.of()));
    }

    @Test
    @DisplayName("null 参数抛出 NullPointerException")
    void nullArgumentsRejected() {
        assertThrows(NullPointerException.class, () -> new WorkspaceEntity(null, "/path", List.of()));
        assertThrows(NullPointerException.class, () -> new WorkspaceEntity("ws-1", null, List.of()));
        var ws = WorkspaceEntity.of("ws-1", "/path");
        assertThrows(NullPointerException.class, () -> ws.attachSession(null));
        assertThrows(NullPointerException.class, () -> ws.detachSession(null));
    }

    @Test
    @DisplayName("attachedSessionIds 不可变")
    void attachedSessionIdsIsImmutable() {
        var ws = WorkspaceEntity.of("ws-1", "/project", List.of("s1"));
        assertThrows(UnsupportedOperationException.class, () -> ws.attachedSessionIds().add("s2"));
    }
}
