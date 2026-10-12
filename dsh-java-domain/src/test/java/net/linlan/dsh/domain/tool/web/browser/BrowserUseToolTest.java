package net.linlan.dsh.domain.tool.web.browser;

import net.linlan.dsh.domain.tool.web.browser.adapter.port.IBrowserUsePort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("T3.038 BrowserUseTool 浏览器使用工具")
class BrowserUseToolTest {

    @Test
    @DisplayName("navigate 合法 URL 调用端口")
    void navigateDelegates() {
        var port = new StubBrowserPort();
        var tool = new BrowserUseTool(port);
        tool.navigate("https://example.com");
        assertEquals("https://example.com", port.lastNavigatedUrl);
    }

    @Test
    @DisplayName("navigate http URL 也支持")
    void navigateHttp() {
        var port = new StubBrowserPort();
        var tool = new BrowserUseTool(port);
        tool.navigate("http://example.com");
        assertEquals("http://example.com", port.lastNavigatedUrl);
    }

    @Test
    @DisplayName("navigate 空 URL 抛异常")
    void navigateBlankThrows() {
        var tool = new BrowserUseTool(new StubBrowserPort());
        assertThrows(IllegalArgumentException.class, () -> tool.navigate(""));
        assertThrows(IllegalArgumentException.class, () -> tool.navigate(null));
    }

    @Test
    @DisplayName("navigate 非法 URL 抛异常")
    void navigateInvalidThrows() {
        var tool = new BrowserUseTool(new StubBrowserPort());
        assertThrows(IllegalArgumentException.class, () -> tool.navigate("not-a-url"));
        assertThrows(IllegalArgumentException.class, () -> tool.navigate("ftp://example.com"));
    }

    @Test
    @DisplayName("click 调用端口")
    void clickDelegates() {
        var port = new StubBrowserPort();
        var tool = new BrowserUseTool(port);
        tool.click("#button");
        assertEquals("#button", port.lastClickedSelector);
    }

    @Test
    @DisplayName("click 空选择器抛异常")
    void clickBlankThrows() {
        var tool = new BrowserUseTool(new StubBrowserPort());
        assertThrows(IllegalArgumentException.class, () -> tool.click(""));
        assertThrows(IllegalArgumentException.class, () -> tool.click(null));
    }

    @Test
    @DisplayName("input 调用端口")
    void inputDelegates() {
        var port = new StubBrowserPort();
        var tool = new BrowserUseTool(port);
        tool.input("#field", "hello");
        assertEquals("#field", port.lastInputSelector);
        assertEquals("hello", port.lastInputText);
    }

    @Test
    @DisplayName("input 空选择器抛异常")
    void inputBlankSelectorThrows() {
        var tool = new BrowserUseTool(new StubBrowserPort());
        assertThrows(IllegalArgumentException.class, () -> tool.input("", "text"));
        assertThrows(IllegalArgumentException.class, () -> tool.input(null, "text"));
    }

    @Test
    @DisplayName("input null 文本抛异常")
    void inputNullTextThrows() {
        var tool = new BrowserUseTool(new StubBrowserPort());
        assertThrows(IllegalArgumentException.class, () -> tool.input("#field", null));
    }

    @Test
    @DisplayName("screenshot 返回结果")
    void screenshotReturnsResult() {
        var port = new StubBrowserPort();
        port.screenshotResult = "base64data";
        var tool = new BrowserUseTool(port);
        assertEquals("base64data", tool.screenshot());
    }

    @Test
    @DisplayName("screenshot null 抛异常")
    void screenshotNullThrows() {
        var port = new StubBrowserPort();
        port.screenshotResult = null;
        var tool = new BrowserUseTool(port);
        assertThrows(IllegalStateException.class, tool::screenshot);
    }

    @Test
    @DisplayName("getText 返回文本")
    void getTextReturnsText() {
        var port = new StubBrowserPort();
        port.textResult = "page text";
        var tool = new BrowserUseTool(port);
        assertEquals("page text", tool.getText("#content"));
    }

    @Test
    @DisplayName("getText null 结果返回空字符串")
    void getTextNullReturnsEmpty() {
        var port = new StubBrowserPort();
        port.textResult = null;
        var tool = new BrowserUseTool(port);
        assertEquals("", tool.getText("#content"));
    }

    @Test
    @DisplayName("getText 空选择器抛异常")
    void getTextBlankSelectorThrows() {
        var tool = new BrowserUseTool(new StubBrowserPort());
        assertThrows(IllegalArgumentException.class, () -> tool.getText(""));
        assertThrows(IllegalArgumentException.class, () -> tool.getText(null));
    }

    @Test
    @DisplayName("null port 抛异常")
    void nullPortThrows() {
        assertThrows(NullPointerException.class, () -> new BrowserUseTool(null));
    }

    static final class StubBrowserPort implements IBrowserUsePort {
        String lastNavigatedUrl;
        String lastClickedSelector;
        String lastInputSelector;
        String lastInputText;
        String screenshotResult = "";
        String textResult = "";

        @Override
        public void navigate(String url) {
            lastNavigatedUrl = url;
        }

        @Override
        public void click(String selector) {
            lastClickedSelector = selector;
        }

        @Override
        public void input(String selector, String text) {
            lastInputSelector = selector;
            lastInputText = text;
        }

        @Override
        public String screenshot() {
            return screenshotResult;
        }

        @Override
        public String getText(String selector) {
            return textResult;
        }
    }
}
