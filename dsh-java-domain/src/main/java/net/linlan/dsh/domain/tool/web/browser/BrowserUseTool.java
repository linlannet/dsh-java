package net.linlan.dsh.domain.tool.web.browser;

import net.linlan.dsh.domain.tool.web.browser.adapter.port.IBrowserUsePort;

import java.util.Objects;
import java.util.regex.Pattern;

public final class BrowserUseTool {

    private static final Pattern URL_PATTERN = Pattern.compile("^https?://.+");

    private final IBrowserUsePort port;

    public BrowserUseTool(IBrowserUsePort port) {
        Objects.requireNonNull(port, "port");
        this.port = port;
    }

    public void navigate(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("url required");
        }
        if (!URL_PATTERN.matcher(url).matches()) {
            throw new IllegalArgumentException("invalid url: " + url);
        }
        port.navigate(url);
    }

    public void click(String selector) {
        if (selector == null || selector.isBlank()) {
            throw new IllegalArgumentException("selector required");
        }
        port.click(selector);
    }

    public void input(String selector, String text) {
        if (selector == null || selector.isBlank()) {
            throw new IllegalArgumentException("selector required");
        }
        if (text == null) {
            throw new IllegalArgumentException("text required");
        }
        port.input(selector, text);
    }

    public String screenshot() {
        String result = port.screenshot();
        if (result == null) {
            throw new IllegalStateException("screenshot returned null");
        }
        return result;
    }

    public String getText(String selector) {
        if (selector == null || selector.isBlank()) {
            throw new IllegalArgumentException("selector required");
        }
        String text = port.getText(selector);
        return text == null ? "" : text;
    }
}
