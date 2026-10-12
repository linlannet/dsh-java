package net.linlan.dsh.domain.sdk.feedback;

import net.linlan.dsh.domain.agent.service.compaction.SlashCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MessageFeedbackService 消息反馈")
class MessageFeedbackServiceTest {

    @Test
    @DisplayName("rate 有效评分存储并返回成功")
    void rateValid() {
        var service = new MessageFeedbackService();
        String result = service.rate("msg-1", 5);
        assertTrue(result.contains("rating=5"));
        var feedback = service.getFeedback("msg-1");
        assertNotNull(feedback);
        assertEquals(5, feedback.rating());
    }

    @Test
    @DisplayName("rate 评分越界抛出 IllegalArgumentException")
    void rateOutOfRange() {
        var service = new MessageFeedbackService();
        assertThrows(IllegalArgumentException.class, () -> service.rate("m", 0));
        assertThrows(IllegalArgumentException.class, () -> service.rate("m", 6));
        assertThrows(IllegalArgumentException.class, () -> service.rate("m", -1));
    }

    @Test
    @DisplayName("rate 空白 messageId 抛出 IllegalArgumentException")
    void rateBlankMessageId() {
        var service = new MessageFeedbackService();
        assertThrows(IllegalArgumentException.class, () -> service.rate("", 3));
        assertThrows(IllegalArgumentException.class, () -> service.rate("  ", 3));
    }

    @Test
    @DisplayName("rate null messageId 抛出 NPE")
    void rateNullMessageId() {
        var service = new MessageFeedbackService();
        assertThrows(NullPointerException.class, () -> service.rate(null, 3));
    }

    @Test
    @DisplayName("note 存储备注")
    void noteStores() {
        var service = new MessageFeedbackService();
        String result = service.note("msg-2", "great answer");
        assertTrue(result.contains("note=great answer"));
        var feedback = service.getFeedback("msg-2");
        assertNotNull(feedback);
        assertEquals("great answer", feedback.note());
    }

    @Test
    @DisplayName("rate 后 note 保留评分")
    void rateThenNotePreservesRating() {
        var service = new MessageFeedbackService();
        service.rate("msg-3", 4);
        service.note("msg-3", "helpful");
        var feedback = service.getFeedback("msg-3");
        assertEquals(4, feedback.rating());
        assertEquals("helpful", feedback.note());
    }

    @Test
    @DisplayName("note 后 rate 保留备注")
    void noteThenRatePreservesNote() {
        var service = new MessageFeedbackService();
        service.note("msg-4", "initial note");
        service.rate("msg-4", 3);
        var feedback = service.getFeedback("msg-4");
        assertEquals(3, feedback.rating());
        assertEquals("initial note", feedback.note());
    }

    @Test
    @DisplayName("getFeedback 不存在返回 null")
    void getFeedbackMissing() {
        var service = new MessageFeedbackService();
        assertNull(service.getFeedback("nonexistent"));
    }

    @Test
    @DisplayName("getFeedback null 抛出 NPE")
    void getFeedbackNullThrows() {
        var service = new MessageFeedbackService();
        assertThrows(NullPointerException.class, () -> service.getFeedback(null));
    }

    @Test
    @DisplayName("listAll 返回所有反馈")
    void listAllReturnsAll() {
        var service = new MessageFeedbackService();
        service.rate("a", 1);
        service.rate("b", 2);
        assertEquals(2, service.listAll().size());
    }

    @Test
    @DisplayName("FeedbackCommand name 返回 /feedback")
    void feedbackCommandName() {
        var cmd = new FeedbackCommand(new MessageFeedbackService());
        assertEquals("/feedback", cmd.name());
    }

    @Test
    @DisplayName("FeedbackCommand description 非空")
    void feedbackCommandDescription() {
        var cmd = new FeedbackCommand(new MessageFeedbackService());
        assertFalse(cmd.description().isBlank());
    }

    @Test
    @DisplayName("FeedbackCommand 评分参数执行成功")
    void feedbackCommandRate() throws Exception {
        var service = new MessageFeedbackService();
        var cmd = new FeedbackCommand(service);
        String result = cmd.execute("msg-5 4", new SlashCommand.SlashCommandContext("s", "/tmp")).get();
        assertTrue(result.contains("rating=4"));
        assertEquals(4, service.getFeedback("msg-5").rating());
    }

    @Test
    @DisplayName("FeedbackCommand note: 参数执行成功")
    void feedbackCommandNote() throws Exception {
        var service = new MessageFeedbackService();
        var cmd = new FeedbackCommand(service);
        String result = cmd.execute("msg-6 note:good", new SlashCommand.SlashCommandContext("s", "/tmp")).get();
        assertTrue(result.contains("note=good"));
    }

    @Test
    @DisplayName("FeedbackCommand 空参数返回 usage")
    void feedbackCommandEmptyArgs() throws Exception {
        var cmd = new FeedbackCommand(new MessageFeedbackService());
        String result = cmd.execute("", new SlashCommand.SlashCommandContext("s", "/tmp")).get();
        assertTrue(result.startsWith("usage:"));
    }

    @Test
    @DisplayName("FeedbackCommand 单参数返回 usage")
    void feedbackCommandSingleArg() throws Exception {
        var cmd = new FeedbackCommand(new MessageFeedbackService());
        String result = cmd.execute("onlyId", new SlashCommand.SlashCommandContext("s", "/tmp")).get();
        assertTrue(result.startsWith("usage:"));
    }

    @Test
    @DisplayName("FeedbackCommand 非法评分返回错误信息")
    void feedbackCommandInvalidRating() throws Exception {
        var cmd = new FeedbackCommand(new MessageFeedbackService());
        String result = cmd.execute("msg abc", new SlashCommand.SlashCommandContext("s", "/tmp")).get();
        assertTrue(result.startsWith("invalid rating"));
    }

    @Test
    @DisplayName("FeedbackCommand 越界评分返回错误信息")
    void feedbackCommandOutOfRangeRating() throws Exception {
        var cmd = new FeedbackCommand(new MessageFeedbackService());
        String result = cmd.execute("msg 99", new SlashCommand.SlashCommandContext("s", "/tmp")).get();
        assertTrue(result.startsWith("error"));
    }

    @Test
    @DisplayName("FeedbackCommand 实现 SlashCommand 接口")
    void feedbackCommandImplementsInterface() {
        var cmd = new FeedbackCommand(new MessageFeedbackService());
        assertTrue(cmd instanceof SlashCommand);
    }
}
