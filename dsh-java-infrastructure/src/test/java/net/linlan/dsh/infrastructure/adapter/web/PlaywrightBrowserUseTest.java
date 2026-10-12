package net.linlan.dsh.infrastructure.adapter.web;

import net.linlan.dsh.domain.tool.web.browser.adapter.port.IBrowserUsePort;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PlaywrightBrowserUseTest {

    @Test
    void implementsBrowserUsePort() {
        PlaywrightBrowserUse b = new PlaywrightBrowserUse();
        assertTrue(b instanceof IBrowserUsePort);
    }

    @Test
    void defaultConstructor() {
        PlaywrightBrowserUse b = new PlaywrightBrowserUse();
        assertNotNull(b);
    }

    @Test
    void customConstructor() throws Exception {
        Path tmp = Files.createTempDirectory("pw-test");
        try {
            PlaywrightBrowserUse b = new PlaywrightBrowserUse("npx", 10, tmp);
            assertNotNull(b);
        } finally {
            deleteRecursively(tmp);
        }
    }

    @Test
    void navigateThrowsWhenNpxUnavailable() throws Exception {
        Path tmp = Files.createTempDirectory("pw-test");
        try {
            PlaywrightBrowserUse b = new PlaywrightBrowserUse("nonexistent-npx-cmd-xyz", 5, tmp);
            assertThrows(IllegalStateException.class, () -> b.navigate("http://example.com"));
        } finally {
            deleteRecursively(tmp);
        }
    }

    @Test
    void clickThrowsWhenNpxUnavailable() throws Exception {
        Path tmp = Files.createTempDirectory("pw-test");
        try {
            PlaywrightBrowserUse b = new PlaywrightBrowserUse("nonexistent-npx-cmd-xyz", 5, tmp);
            assertThrows(IllegalStateException.class, () -> b.click("#btn"));
        } finally {
            deleteRecursively(tmp);
        }
    }

    @Test
    void inputThrowsWhenNpxUnavailable() throws Exception {
        Path tmp = Files.createTempDirectory("pw-test");
        try {
            PlaywrightBrowserUse b = new PlaywrightBrowserUse("nonexistent-npx-cmd-xyz", 5, tmp);
            assertThrows(IllegalStateException.class, () -> b.input("#field", "text"));
        } finally {
            deleteRecursively(tmp);
        }
    }

    @Test
    void screenshotThrowsWhenNpxUnavailable() throws Exception {
        Path tmp = Files.createTempDirectory("pw-test");
        try {
            PlaywrightBrowserUse b = new PlaywrightBrowserUse("nonexistent-npx-cmd-xyz", 5, tmp);
            assertThrows(IllegalStateException.class, () -> b.screenshot());
        } finally {
            deleteRecursively(tmp);
        }
    }

    @Test
    void getTextThrowsWhenNpxUnavailable() throws Exception {
        Path tmp = Files.createTempDirectory("pw-test");
        try {
            PlaywrightBrowserUse b = new PlaywrightBrowserUse("nonexistent-npx-cmd-xyz", 5, tmp);
            assertThrows(IllegalStateException.class, () -> b.getText("#content"));
        } finally {
            deleteRecursively(tmp);
        }
    }

    @Test
    void navigateWithNullUrlThrows() throws Exception {
        Path tmp = Files.createTempDirectory("pw-test");
        try {
            PlaywrightBrowserUse b = new PlaywrightBrowserUse("nonexistent-npx-cmd-xyz", 5, tmp);
            assertThrows(IllegalStateException.class, () -> b.navigate(null));
        } finally {
            deleteRecursively(tmp);
        }
    }

    @Test
    void navigateThrowsWhenScriptReturnsNonZero() throws Exception {
        Path tmp = Files.createTempDirectory("pw-test");
        try {
            PlaywrightBrowserUse b = new PlaywrightBrowserUse("false", 5, tmp);
            assertThrows(IllegalStateException.class, () -> b.navigate("http://example.com"));
        } finally {
            deleteRecursively(tmp);
        }
    }

    @Test
    void clickThrowsWhenScriptReturnsNonZero() throws Exception {
        Path tmp = Files.createTempDirectory("pw-test");
        try {
            PlaywrightBrowserUse b = new PlaywrightBrowserUse("false", 5, tmp);
            assertThrows(IllegalStateException.class, () -> b.click("#btn"));
        } finally {
            deleteRecursively(tmp);
        }
    }

    @Test
    void inputThrowsWhenScriptReturnsNonZero() throws Exception {
        Path tmp = Files.createTempDirectory("pw-test");
        try {
            PlaywrightBrowserUse b = new PlaywrightBrowserUse("false", 5, tmp);
            assertThrows(IllegalStateException.class, () -> b.input("#field", "text"));
        } finally {
            deleteRecursively(tmp);
        }
    }

    @Test
    void screenshotThrowsWhenScriptReturnsNonZero() throws Exception {
        Path tmp = Files.createTempDirectory("pw-test");
        try {
            PlaywrightBrowserUse b = new PlaywrightBrowserUse("false", 5, tmp);
            assertThrows(IllegalStateException.class, () -> b.screenshot());
        } finally {
            deleteRecursively(tmp);
        }
    }

    @Test
    void getTextThrowsWhenScriptReturnsNonZero() throws Exception {
        Path tmp = Files.createTempDirectory("pw-test");
        try {
            PlaywrightBrowserUse b = new PlaywrightBrowserUse("false", 5, tmp);
            assertThrows(IllegalStateException.class, () -> b.getText("#content"));
        } finally {
            deleteRecursively(tmp);
        }
    }

    @Test
    void navigateTimesOutWithSleepCommand() throws Exception {
        Path tmp = Files.createTempDirectory("pw-test");
        try {
            PlaywrightBrowserUse b = new PlaywrightBrowserUse("sleep", 1, tmp);
            assertThrows(IllegalStateException.class, () -> b.navigate("http://example.com"));
        } finally {
            deleteRecursively(tmp);
        }
    }

    private void deleteRecursively(Path path) throws Exception {
        if (Files.isDirectory(path)) {
            try (var stream = Files.list(path)) {
                stream.forEach(p -> {
                    try { deleteRecursively(p); } catch (Exception ignored) { }
                });
            }
        }
        Files.deleteIfExists(path);
    }
}
