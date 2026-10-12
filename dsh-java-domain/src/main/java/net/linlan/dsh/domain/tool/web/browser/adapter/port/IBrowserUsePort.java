package net.linlan.dsh.domain.tool.web.browser.adapter.port;

public interface IBrowserUsePort {

    void navigate(String url);

    void click(String selector);

    void input(String selector, String text);

    String screenshot();

    String getText(String selector);
}
