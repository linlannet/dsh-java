package net.linlan.dsh.domain.session.inspector.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SessionInspectorService 会话检查器")
class SessionInspectorServiceTest {
    @Test
    @DisplayName("inspectRawLog 返回分页日志")
    void inspectRawLog() {
        var service = new SessionInspectorService();
        var log = service.inspectRawLog("s1", 0, 10);
        assertNotNull(log);
    }

    @Test
    @DisplayName("inspectVirtualTable 返回虚拟表格")
    void inspectVirtualTable() {
        var service = new SessionInspectorService();
        var table = service.inspectVirtualTable("s1");
        assertNotNull(table);
    }

    @Test
    @DisplayName("locateToChat 返回定位锚点")
    void locateToChat() {
        var service = new SessionInspectorService();
        assertEquals("s1#node1", service.locateToChat("s1", "node1"));
    }
}
