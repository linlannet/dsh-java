package net.linlan.dsh.domain.tool.jobs;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JobControlTool 作业管理")
class JobControlToolTest {

    @Test
    @DisplayName("createJob 返回包含 id 的成功消息")
    void createJobReturnsSuccess() {
        var tool = new JobControlTool();
        String result = tool.createJob("backup", "0 2 * * *", "rsync -a /data /backup");
        assertTrue(result.startsWith("job created: id="));
        assertTrue(result.contains("name=backup"));
        assertTrue(result.contains("cron=0 2 * * *"));
        assertTrue(result.contains("command=rsync -a /data /backup"));
        assertEquals(1, tool.size());
    }

    @Test
    @DisplayName("createJob 多次调用生成不同 id")
    void createJobUniqueIds() {
        var tool = new JobControlTool();
        String r1 = tool.createJob("a", "0 1 * * *", "cmd1");
        String r2 = tool.createJob("b", "0 2 * * *", "cmd2");
        assertNotEquals(r1, r2);
        assertEquals(2, tool.size());
    }

    @Test
    @DisplayName("getJob 返回已创建的作业")
    void getJobReturnsEntry() {
        var tool = new JobControlTool();
        String created = tool.createJob("deploy", "0 9 * * *", "kubectl apply -f k8s/");
        String id = extractId(created);
        var entry = tool.getJob(id);
        assertNotNull(entry);
        assertEquals(id, entry.id());
        assertEquals("deploy", entry.name());
        assertEquals("0 9 * * *", entry.cron());
        assertEquals("kubectl apply -f k8s/", entry.command());
        assertTrue(entry.active());
        assertNotNull(entry.createdAt());
    }

    @Test
    @DisplayName("getJob 不存在 id 返回 null")
    void getJobNotFound() {
        var tool = new JobControlTool();
        assertNull(tool.getJob("nonexistent"));
    }

    @Test
    @DisplayName("cancelJob 将作业标记为非活跃")
    void cancelJobDeactivates() {
        var tool = new JobControlTool();
        String created = tool.createJob("test", "*/5 * * * *", "echo hi");
        String id = extractId(created);
        assertTrue(tool.getJob(id).active());
        String result = tool.cancelJob(id);
        assertTrue(result.startsWith("job cancelled:"));
        assertFalse(tool.getJob(id).active());
    }

    @Test
    @DisplayName("cancelJob 不存在 id 返回 not found")
    void cancelJobNotFound() {
        var tool = new JobControlTool();
        assertEquals("job not found: id=nonexistent", tool.cancelJob("nonexistent"));
    }

    @Test
    @DisplayName("listJobs 返回所有作业")
    void listJobsReturnsAll() {
        var tool = new JobControlTool();
        tool.createJob("a", "0 1 * * *", "cmd1");
        tool.createJob("b", "0 2 * * *", "cmd2");
        var jobs = tool.listJobs();
        assertEquals(2, jobs.size());
    }

    @Test
    @DisplayName("listJobs 返回可变副本")
    void listJobsIsMutableCopy() {
        var tool = new JobControlTool();
        tool.createJob("a", "0 1 * * *", "cmd1");
        var jobs = tool.listJobs();
        jobs.clear();
        assertEquals(1, tool.size());
    }

    @Test
    @DisplayName("空 name 抛出 IllegalArgumentException")
    void blankNameThrows() {
        var tool = new JobControlTool();
        assertThrows(IllegalArgumentException.class, () -> tool.createJob("", "cron", "cmd"));
        assertThrows(IllegalArgumentException.class, () -> tool.createJob("  ", "cron", "cmd"));
    }

    @Test
    @DisplayName("空 cron 抛出 IllegalArgumentException")
    void blankCronThrows() {
        var tool = new JobControlTool();
        assertThrows(IllegalArgumentException.class, () -> tool.createJob("name", "", "cmd"));
    }

    @Test
    @DisplayName("空 command 抛出 IllegalArgumentException")
    void blankCommandThrows() {
        var tool = new JobControlTool();
        assertThrows(IllegalArgumentException.class, () -> tool.createJob("name", "cron", ""));
    }

    @Test
    @DisplayName("null 参数抛出 NullPointerException")
    void nullArgsThrowNpe() {
        var tool = new JobControlTool();
        assertThrows(NullPointerException.class, () -> tool.createJob(null, "cron", "cmd"));
        assertThrows(NullPointerException.class, () -> tool.createJob("name", null, "cmd"));
        assertThrows(NullPointerException.class, () -> tool.createJob("name", "cron", null));
        assertThrows(NullPointerException.class, () -> tool.getJob(null));
        assertThrows(NullPointerException.class, () -> tool.cancelJob(null));
    }

    private String extractId(String message) {
        int start = message.indexOf("id=") + 3;
        int end = message.indexOf(",", start);
        return message.substring(start, end);
    }
}
