package net.linlan.dsh.infrastructure.adapter.plugin;

import net.linlan.dsh.domain.plugin.registry.adapter.port.IRemoteInventoryPort;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class HttpRemoteInventoryTest {

    @Test
    void implementsRemoteInventoryPort() {
        HttpRemoteInventory inv = new HttpRemoteInventory("http://localhost:9999");
        assertTrue(inv instanceof IRemoteInventoryPort);
    }

    @Test
    void defaultConstructorBuildsClient() {
        HttpRemoteInventory inv = new HttpRemoteInventory("http://localhost:9999");
        assertNotNull(inv);
    }

    @Test
    void customConstructorNullClientThrows() {
        assertThrows(NullPointerException.class, () -> new HttpRemoteInventory(null, "http://localhost"));
    }

    @Test
    void customConstructorNullBaseUrlBecomesEmpty() {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
        HttpRemoteInventory inv = new HttpRemoteInventory(client, null);
        assertNotNull(inv);
        assertTrue(inv.listPlugins().isEmpty());
    }

    @Test
    void listPluginsReturnsEmptyOnConnectionFailure() {
        HttpRemoteInventory inv = new HttpRemoteInventory("http://localhost:1");
        assertTrue(inv.listPlugins().isEmpty());
    }

    @Test
    void getPluginNullThrowsNpe() {
        HttpRemoteInventory inv = new HttpRemoteInventory("http://localhost:1");
        assertThrows(NullPointerException.class, () -> inv.getPlugin(null));
    }

    @Test
    void getPluginReturnsEmptyOnConnectionFailure() {
        HttpRemoteInventory inv = new HttpRemoteInventory("http://localhost:1");
        assertTrue(inv.getPlugin("some-plugin").isEmpty());
    }

    @Test
    void customConstructorWithClient() {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
        HttpRemoteInventory inv = new HttpRemoteInventory(client, "http://localhost:1");
        assertTrue(inv.listPlugins().isEmpty());
    }

    @Test
    void listPluginsWithInvalidUrlReturnsEmpty() {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
        HttpRemoteInventory inv = new HttpRemoteInventory(client, "not-a-url");
        assertTrue(inv.listPlugins().isEmpty());
    }

    @Test
    void getPluginWithInvalidUrlReturnsEmpty() {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
        HttpRemoteInventory inv = new HttpRemoteInventory(client, "not-a-url");
        assertTrue(inv.getPlugin("p").isEmpty());
    }

    @Test
    void listPluginsParsesValidResponse() throws Exception {
        String body = "p1|Plugin One|1.0|desc1|http://src1\np2|Plugin Two|2.0|desc2|http://src2";
        com.sun.net.httpserver.HttpServer server = startEchoServer("/plugins", body, 200);
        try {
            int port = server.getAddress().getPort();
            HttpRemoteInventory inv = new HttpRemoteInventory("http://localhost:" + port);
            var plugins = inv.listPlugins();
            assertEquals(2, plugins.size());
            assertEquals("p1", plugins.get(0).id());
            assertEquals("Plugin One", plugins.get(0).name());
            assertEquals("1.0", plugins.get(0).version());
            assertEquals("desc1", plugins.get(0).description());
            assertEquals("http://src1", plugins.get(0).sourceUrl());
            assertEquals("p2", plugins.get(1).id());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void listPluginsSkipsCommentAndShortLines() throws Exception {
        String body = "# comment\nshort\np1|n|v|d|src";
        com.sun.net.httpserver.HttpServer server = startEchoServer("/plugins", body, 200);
        try {
            int port = server.getAddress().getPort();
            HttpRemoteInventory inv = new HttpRemoteInventory("http://localhost:" + port);
            var plugins = inv.listPlugins();
            assertEquals(1, plugins.size());
            assertEquals("p1", plugins.get(0).id());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void listPluginsReturnsEmptyForNon200() throws Exception {
        com.sun.net.httpserver.HttpServer server = startEchoServer("/plugins", "x", 404);
        try {
            int port = server.getAddress().getPort();
            HttpRemoteInventory inv = new HttpRemoteInventory("http://localhost:" + port);
            assertTrue(inv.listPlugins().isEmpty());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void listPluginsHandlesEmptyBody() throws Exception {
        com.sun.net.httpserver.HttpServer server = startEchoServer("/plugins", "", 200);
        try {
            int port = server.getAddress().getPort();
            HttpRemoteInventory inv = new HttpRemoteInventory("http://localhost:" + port);
            assertTrue(inv.listPlugins().isEmpty());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void getPluginParsesValidResponse() throws Exception {
        String body = "pid|Plugin Name|3.1|description|http://source";
        com.sun.net.httpserver.HttpServer server = startEchoServer("/plugins/pid", body, 200);
        try {
            int port = server.getAddress().getPort();
            HttpRemoteInventory inv = new HttpRemoteInventory("http://localhost:" + port);
            var plugin = inv.getPlugin("pid");
            assertTrue(plugin.isPresent());
            assertEquals("pid", plugin.get().id());
            assertEquals("Plugin Name", plugin.get().name());
            assertEquals("3.1", plugin.get().version());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void getPluginReturnsEmptyForNon200() throws Exception {
        com.sun.net.httpserver.HttpServer server = startEchoServer("/plugins/x", "err", 500);
        try {
            int port = server.getAddress().getPort();
            HttpRemoteInventory inv = new HttpRemoteInventory("http://localhost:" + port);
            assertTrue(inv.getPlugin("x").isEmpty());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void getPluginHandlesShortBody() throws Exception {
        com.sun.net.httpserver.HttpServer server = startEchoServer("/plugins/x", "short", 200);
        try {
            int port = server.getAddress().getPort();
            HttpRemoteInventory inv = new HttpRemoteInventory("http://localhost:" + port);
            var plugin = inv.getPlugin("x");
            assertTrue(plugin.isPresent());
            assertEquals("", plugin.get().id());
        } finally {
            server.stop(0);
        }
    }

    private com.sun.net.httpserver.HttpServer startEchoServer(String path, String body, int statusCode) throws Exception {
        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress(0), 0);
        server.createContext(path, exchange -> {
            byte[] resp = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(statusCode, resp.length);
            try (var os = exchange.getResponseBody()) {
                os.write(resp);
            }
        });
        server.start();
        return server;
    }
}
