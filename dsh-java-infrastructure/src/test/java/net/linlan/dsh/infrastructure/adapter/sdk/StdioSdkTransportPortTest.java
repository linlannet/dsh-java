package net.linlan.dsh.infrastructure.adapter.sdk;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StdioSdkTransportPortTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void notifyWritesNewlineDelimitedJsonRpcNotification() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        StdioSdkTransportPort port = newPort(emptyIn(), out);

        port.notify("event/started", Map.of("session", "s1"));

        String frame = out.toString(StandardCharsets.UTF_8).trim();
        assertTrue(frame.contains("\"jsonrpc\":\"2.0\""));
        assertTrue(frame.contains("\"method\":\"event/started\""));
        assertTrue(frame.contains("\"session\":\"s1\""));
        port.close();
    }

    @Test
    void notifyOmitsParamsFieldWhenNull() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        StdioSdkTransportPort port = newPort(emptyIn(), out);

        port.notify("event/ping", null);

        String frame = out.toString(StandardCharsets.UTF_8).trim();
        assertTrue(frame.contains("\"method\":\"event/ping\""));
        assertTrue(!frame.contains("\"params\""));
        port.close();
    }

    @Test
    void onNotificationDispatchesIncomingNotificationFrame() throws Exception {
        PipedInputStream in = new PipedInputStream();
        PipedOutputStream pipe = new PipedOutputStream(in);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        StdioSdkTransportPort port = newPort(in, out);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> receivedMethod = new AtomicReference<>();

        port.onNotification(nf -> {
            receivedMethod.set(nf.method());
            latch.countDown();
        });

        pipe.write("{\"jsonrpc\":\"2.0\",\"method\":\"event/x\",\"params\":{\"a\":1}}\n".getBytes(StandardCharsets.UTF_8));
        pipe.flush();

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertEquals("event/x", receivedMethod.get());
        port.close();
    }

    @Test
    void registerRequestHandlerRepliesToIncomingRequest() throws Exception {
        PipedInputStream in = new PipedInputStream();
        PipedOutputStream pipe = new PipedOutputStream(in);
        CountDownLatch latch = new CountDownLatch(1);
        LatchedOutputStream out = new LatchedOutputStream(latch);
        StdioSdkTransportPort port = newPort(in, out);

        port.registerRequestHandler("echo", params -> params);

        pipe.write("{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"method\":\"echo\",\"params\":\"hello\"}\n".getBytes(StandardCharsets.UTF_8));
        pipe.flush();

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        String response = out.utf8().trim();
        assertTrue(response.contains("\"id\":\"1\""));
        assertTrue(response.contains("\"result\":\"hello\""));
        port.close();
    }

    @Test
    void registerRequestHandlerRepliesWithErrorWhenHandlerThrows() throws Exception {
        PipedInputStream in = new PipedInputStream();
        PipedOutputStream pipe = new PipedOutputStream(in);
        CountDownLatch latch = new CountDownLatch(1);
        LatchedOutputStream out = new LatchedOutputStream(latch);
        StdioSdkTransportPort port = newPort(in, out);

        port.registerRequestHandler("boom", params -> {
            throw new RuntimeException("kaboom");
        });

        pipe.write("{\"jsonrpc\":\"2.0\",\"id\":\"2\",\"method\":\"boom\"}\n".getBytes(StandardCharsets.UTF_8));
        pipe.flush();

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        String response = out.utf8().trim();
        assertTrue(response.contains("\"id\":\"2\""));
        assertTrue(response.contains("\"error\""));
        port.close();
    }

    @Test
    void requestReturnsResultWhenResponseArrives() throws Exception {
        PipedInputStream in = new PipedInputStream();
        PipedOutputStream pipe = new PipedOutputStream(in);
        CountDownLatch requestSent = new CountDownLatch(1);
        LatchedOutputStream out = new LatchedOutputStream(requestSent);
        StdioSdkTransportPort port = newPort(in, out);

        Thread responder = new Thread(() -> {
            try {
                requestSent.await(2, TimeUnit.SECONDS);
                pipe.write("{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"result\":\"ok\"}\n".getBytes(StandardCharsets.UTF_8));
                pipe.flush();
            } catch (Exception ignored) { }
        });
        responder.start();

        Object result = port.request("test/method", Map.of("a", 1));

        assertEquals("ok", result);
        String sentFrame = out.utf8().trim();
        assertTrue(sentFrame.contains("\"method\":\"test/method\""));
        assertTrue(sentFrame.contains("\"id\":\"1\""));
        responder.join(2000);
        port.close();
    }

    @Test
    void requestOmitsParamsWhenNull() throws Exception {
        PipedInputStream in = new PipedInputStream();
        PipedOutputStream pipe = new PipedOutputStream(in);
        CountDownLatch requestSent = new CountDownLatch(1);
        LatchedOutputStream out = new LatchedOutputStream(requestSent);
        StdioSdkTransportPort port = newPort(in, out);

        Thread responder = new Thread(() -> {
            try {
                requestSent.await(2, TimeUnit.SECONDS);
                pipe.write("{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"result\":42}\n".getBytes(StandardCharsets.UTF_8));
                pipe.flush();
            } catch (Exception ignored) { }
        });
        responder.start();

        Object result = port.request("test/noop", null);

        assertEquals(42, result);
        assertTrue(!out.utf8().contains("\"params\""));
        responder.join(2000);
        port.close();
    }

    @Test
    void requestPropagatesErrorWhenResponseContainsError() throws Exception {
        PipedInputStream in = new PipedInputStream();
        PipedOutputStream pipe = new PipedOutputStream(in);
        CountDownLatch requestSent = new CountDownLatch(1);
        LatchedOutputStream out = new LatchedOutputStream(requestSent);
        StdioSdkTransportPort port = newPort(in, out);

        Thread responder = new Thread(() -> {
            try {
                requestSent.await(2, TimeUnit.SECONDS);
                pipe.write("{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"error\":{\"code\":-1,\"message\":\"bad\"}}\n".getBytes(StandardCharsets.UTF_8));
                pipe.flush();
            } catch (Exception ignored) { }
        });
        responder.start();

        assertThrows(RuntimeException.class, () -> port.request("test/fail", null));

        responder.join(2000);
        port.close();
    }

    @Test
    void closeIsIdempotentAndDoesNotThrow() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        StdioSdkTransportPort port = newPort(emptyIn(), out);

        port.close();
        port.close();
    }

    @Test
    void malformedLineIsIgnoredWithoutFailingTransport() throws Exception {
        PipedInputStream in = new PipedInputStream();
        PipedOutputStream pipe = new PipedOutputStream(in);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        StdioSdkTransportPort port = newPort(in, out);
        CountDownLatch latch = new CountDownLatch(1);
        port.onNotification(nf -> latch.countDown());

        pipe.write("not-json-at-all\n".getBytes(StandardCharsets.UTF_8));
        pipe.write("{\"jsonrpc\":\"2.0\",\"method\":\"event/ok\"}\n".getBytes(StandardCharsets.UTF_8));
        pipe.flush();

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        port.close();
    }

    @Test
    void notificationParamsAreForwardedToHandler() throws Exception {
        PipedInputStream in = new PipedInputStream();
        PipedOutputStream pipe = new PipedOutputStream(in);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        StdioSdkTransportPort port = newPort(in, out);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Object> receivedParams = new AtomicReference<>();

        port.onNotification(nf -> {
            receivedParams.set(nf.params());
            latch.countDown();
        });

        pipe.write("{\"jsonrpc\":\"2.0\",\"method\":\"event/p\",\"params\":{\"k\":\"v\"}}\n".getBytes(StandardCharsets.UTF_8));
        pipe.flush();

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertTrue(receivedParams.get() instanceof Map);
        assertEquals("v", ((Map<?, ?>) receivedParams.get()).get("k"));
        port.close();
    }

    private StdioSdkTransportPort newPort(java.io.InputStream in, OutputStream out) {
        return new StdioSdkTransportPort(in, out, mapper);
    }

    private ByteArrayInputStream emptyIn() {
        return new ByteArrayInputStream(new byte[0]);
    }

    private static final class LatchedOutputStream extends OutputStream {
        private final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        private final CountDownLatch latch;

        LatchedOutputStream(CountDownLatch latch) {
            this.latch = latch;
        }

        @Override
        public void write(int b) {
            bos.write(b);
            latch.countDown();
        }

        @Override
        public void write(byte[] b, int off, int len) {
            bos.write(b, off, len);
            latch.countDown();
        }

        String utf8() {
            return bos.toString(StandardCharsets.UTF_8);
        }
    }
}
