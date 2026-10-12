package net.linlan.dsh.domain.tool.schedule;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ScheduleTool 提醒任务管理")
class ScheduleToolTest {

    @Test
    @DisplayName("createSchedule 返回包含 id 的成功消息")
    void createScheduleReturnsSuccessMessage() {
        var tool = new ScheduleTool();
        String result = tool.createSchedule("每日站会提醒", "0 9 * * *");
        assertTrue(result.startsWith("schedule created: id="));
        assertTrue(result.contains("description=每日站会提醒"));
        assertTrue(result.contains("cron=0 9 * * *"));
        assertEquals(1, tool.size());
    }

    @Test
    @DisplayName("createSchedule 多次调用生成不同 id")
    void createScheduleGeneratesUniqueIds() {
        var tool = new ScheduleTool();
        String r1 = tool.createSchedule("a", "0 9 * * *");
        String r2 = tool.createSchedule("b", "0 10 * * *");
        assertNotEquals(r1, r2);
        assertEquals(2, tool.size());
    }

    @Test
    @DisplayName("modifySchedule 更新已存在提醒的描述")
    void modifyScheduleUpdatesDescription() {
        var tool = new ScheduleTool();
        String created = tool.createSchedule("原始描述", "0 9 * * *");
        String id = extractId(created);
        String result = tool.modifySchedule(id, "更新后的描述");
        assertTrue(result.startsWith("schedule modified:"));
        assertTrue(result.contains("description=更新后的描述"));
        var entry = tool.getSchedule(id);
        assertEquals("更新后的描述", entry.description());
        assertEquals("0 9 * * *", entry.cron());
    }

    @Test
    @DisplayName("modifySchedule 不存在 id 返回 not found")
    void modifyScheduleNotFound() {
        var tool = new ScheduleTool();
        String result = tool.modifySchedule("nonexistent-id", "desc");
        assertEquals("schedule not found: id=nonexistent-id", result);
    }

    @Test
    @DisplayName("cancelSchedule 将提醒标记为非活跃")
    void cancelScheduleDeactivates() {
        var tool = new ScheduleTool();
        String created = tool.createSchedule("提醒", "0 9 * * *");
        String id = extractId(created);
        assertTrue(tool.getSchedule(id).active());
        String result = tool.cancelSchedule(id);
        assertTrue(result.startsWith("schedule cancelled:"));
        assertFalse(tool.getSchedule(id).active());
    }

    @Test
    @DisplayName("cancelSchedule 不存在 id 返回 not found")
    void cancelScheduleNotFound() {
        var tool = new ScheduleTool();
        String result = tool.cancelSchedule("nonexistent-id");
        assertEquals("schedule not found: id=nonexistent-id", result);
    }

    @Test
    @DisplayName("getSchedule 返回已创建的提醒条目")
    void getScheduleReturnsEntry() {
        var tool = new ScheduleTool();
        String created = tool.createSchedule("测试提醒", "*/5 * * * *");
        String id = extractId(created);
        var entry = tool.getSchedule(id);
        assertNotNull(entry);
        assertEquals(id, entry.id());
        assertEquals("测试提醒", entry.description());
        assertEquals("*/5 * * * *", entry.cron());
        assertTrue(entry.active());
        assertNotNull(entry.createdAt());
    }

    @Test
    @DisplayName("getSchedule 不存在 id 返回 null")
    void getScheduleNotFoundReturnsNull() {
        var tool = new ScheduleTool();
        assertNull(tool.getSchedule("nonexistent"));
    }

    @Test
    @DisplayName("空 description 抛出 IllegalArgumentException")
    void blankDescriptionThrows() {
        var tool = new ScheduleTool();
        assertThrows(IllegalArgumentException.class, () -> tool.createSchedule("", "0 9 * * *"));
        assertThrows(IllegalArgumentException.class, () -> tool.createSchedule("  ", "0 9 * * *"));
    }

    @Test
    @DisplayName("空 cron 抛出 IllegalArgumentException")
    void blankCronThrows() {
        var tool = new ScheduleTool();
        assertThrows(IllegalArgumentException.class, () -> tool.createSchedule("desc", ""));
        assertThrows(IllegalArgumentException.class, () -> tool.createSchedule("desc", "  "));
    }

    @Test
    @DisplayName("null 参数抛出 NullPointerException")
    void nullArgumentsRejected() {
        var tool = new ScheduleTool();
        assertThrows(NullPointerException.class, () -> tool.createSchedule(null, "cron"));
        assertThrows(NullPointerException.class, () -> tool.createSchedule("desc", null));
        assertThrows(NullPointerException.class, () -> tool.modifySchedule(null, "desc"));
        assertThrows(NullPointerException.class, () -> tool.modifySchedule("id", null));
        assertThrows(NullPointerException.class, () -> tool.cancelSchedule(null));
        assertThrows(NullPointerException.class, () -> tool.getSchedule(null));
    }

    private String extractId(String message) {
        int start = message.indexOf("id=") + 3;
        int end = message.indexOf(",", start);
        return message.substring(start, end);
    }
}
