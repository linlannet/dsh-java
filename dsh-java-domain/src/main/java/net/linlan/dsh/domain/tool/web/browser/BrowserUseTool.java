package net.linlan.dsh.domain.tool.web.browser;

import net.linlan.dsh.domain.common.error.HarnessError;
import net.linlan.dsh.domain.tool.model.error.ToolArgsError;
import net.linlan.dsh.domain.tool.web.browser.adapter.port.IBrowserUsePort;

import java.util.Objects;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * BrowserUseTool 封装浏览器页面操作。
 *
 * <p>调用流程：参数校验 → 调用 {@link IBrowserUsePort} → 失败时抛出分类错误。</p>
 * <p>示例：URL 非法返回 {@code INVALID_ARGS}，端口异常返回 {@code BROWSER_EXECUTION_FAILED}。</p>
 */
public final class BrowserUseTool {

    private static final Pattern URL_PATTERN = Pattern.compile("^https?://.+");

    private final IBrowserUsePort port;

    public BrowserUseTool(IBrowserUsePort port) {
        Objects.requireNonNull(port, "port");
        this.port = port;
    }

    public void navigate(String url) {
        if (url == null || url.isBlank()) {
            throw new ToolArgsError("url required");
        }
        if (!URL_PATTERN.matcher(url).matches()) {
            throw new ToolArgsError("invalid url: " + url);
        }
        run("navigate", () -> port.navigate(url));
    }

    public void click(String selector) {
        if (selector == null || selector.isBlank()) {
            throw new ToolArgsError("selector required");
        }
        run("click", () -> port.click(selector));
    }

    public void input(String selector, String text) {
        if (selector == null || selector.isBlank()) {
            throw new ToolArgsError("selector required");
        }
        if (text == null) {
            throw new ToolArgsError("text required");
        }
        run("input", () -> port.input(selector, text));
    }

    public String screenshot() {
        String result = call("screenshot", port::screenshot);
        if (result == null) {
            throw new BrowserUseError(BrowserUseError.EMPTY_RESULT,
                    "browser screenshot returned null", null);
        }
        return result;
    }

    public String getText(String selector) {
        if (selector == null || selector.isBlank()) {
            throw new ToolArgsError("selector required");
        }
        String text = call("getText", () -> port.getText(selector));
        return text == null ? "" : text;
    }

    private <T> T call(String action, Supplier<T> operation) {
        try {
            return operation.get();
        } catch (HarnessError error) {
            throw error;
        } catch (RuntimeException error) {
            throw new BrowserUseError(BrowserUseError.EXECUTION_FAILED,
                    "browser " + action + " failed: " + error.getMessage(), error);
        }
    }

    private void run(String action, Runnable operation) {
        call(action, () -> {
            operation.run();
            return null;
        });
    }
}
