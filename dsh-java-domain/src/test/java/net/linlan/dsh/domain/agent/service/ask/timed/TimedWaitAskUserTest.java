package net.linlan.dsh.domain.agent.service.ask.timed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class TimedWaitAskUserTest {

    @Test
    void shouldReturnDefaultValueOnTimeout() {
        TimedWaitAskUser asker = new TimedWaitAskUser();

        TimedWaitAskUser.AskResult result = asker.ask("what?", Duration.ofMillis(50), "fallback");

        assertNotNull(result);
        assertEquals("fallback", result.reply());
        assertTrue(result.timedOut());
    }

    @Test
    void shouldReturnReplyWhenProvidedBeforeTimeout() throws Exception {
        TimedWaitAskUser asker = new TimedWaitAskUser();
        AtomicReference<TimedWaitAskUser.AskResult> resultRef = new AtomicReference<>();

        Thread askThread = new Thread(() -> {
            resultRef.set(asker.ask("question?", Duration.ofMillis(3000), "default"));
        });
        askThread.start();

        String questionId = waitForPending(asker);
        assertNotNull(questionId);

        boolean accepted = asker.acceptLateReply(questionId, "real-answer");
        assertTrue(accepted);

        askThread.join(3000);
        TimedWaitAskUser.AskResult result = resultRef.get();
        assertNotNull(result);
        assertEquals("real-answer", result.reply());
        assertFalse(result.timedOut());
    }

    @Test
    void shouldReturnFalseForUnknownQuestionId() {
        TimedWaitAskUser asker = new TimedWaitAskUser();

        boolean accepted = asker.acceptLateReply("unknown-id", "reply");

        assertFalse(accepted);
    }

    @Test
    void shouldReturnFalseForBlankQuestionId() {
        TimedWaitAskUser asker = new TimedWaitAskUser();

        boolean accepted = asker.acceptLateReply("  ", "reply");

        assertFalse(accepted);
    }

    @Test
    void shouldRejectBlankQuestion() {
        TimedWaitAskUser asker = new TimedWaitAskUser();

        assertThrows(IllegalArgumentException.class, () ->
                asker.ask("  ", Duration.ofMillis(100), "default"));
    }

    @Test
    void shouldRejectNullTimeout() {
        TimedWaitAskUser asker = new TimedWaitAskUser();

        assertThrows(IllegalArgumentException.class, () ->
                asker.ask("q", null, "default"));
    }

    @Test
    void shouldRejectNegativeTimeout() {
        TimedWaitAskUser asker = new TimedWaitAskUser();

        assertThrows(IllegalArgumentException.class, () ->
                asker.ask("q", Duration.ofMillis(-1), "default"));
    }

    @Test
    void shouldNotHavePendingAfterTimeout() throws Exception {
        TimedWaitAskUser asker = new TimedWaitAskUser();

        TimedWaitAskUser.AskResult result = asker.ask("q", Duration.ofMillis(20), "default");

        Thread.sleep(50);
        assertFalse(asker.hasPending(result.questionId()));
    }

    @Test
    void shouldGenerateUniqueQuestionIds() {
        TimedWaitAskUser asker = new TimedWaitAskUser();

        TimedWaitAskUser.AskResult r1 = asker.ask("q1", Duration.ofMillis(10), "d1");
        TimedWaitAskUser.AskResult r2 = asker.ask("q2", Duration.ofMillis(10), "d2");

        assertFalse(r1.questionId().equals(r2.questionId()));
    }

    @Test
    void shouldNotAcceptLateReplyAfterTimeout() throws Exception {
        TimedWaitAskUser asker = new TimedWaitAskUser();

        TimedWaitAskUser.AskResult result = asker.ask("q", Duration.ofMillis(20), "default");
        Thread.sleep(50);

        boolean accepted = asker.acceptLateReply(result.questionId(), "late");
        assertFalse(accepted);
    }

    @Test
    void shouldListPendingQuestionIds() throws Exception {
        TimedWaitAskUser asker = new TimedWaitAskUser();
        AtomicReference<TimedWaitAskUser.AskResult> resultRef = new AtomicReference<>();

        Thread askThread = new Thread(() -> {
            resultRef.set(asker.ask("q", Duration.ofMillis(3000), "default"));
        });
        askThread.start();

        Thread.sleep(50);
        List<String> pendingIds = asker.listPendingQuestionIds();
        assertEquals(1, pendingIds.size());

        asker.acceptLateReply(pendingIds.get(0), "answer");
        askThread.join(3000);
    }

    private String waitForPending(TimedWaitAskUser asker) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            List<String> ids = asker.listPendingQuestionIds();
            if (!ids.isEmpty()) {
                return ids.get(0);
            }
            Thread.sleep(10);
        }
        return null;
    }
}
