package net.linlan.dsh.infrastructure.adapter.web;

import net.linlan.dsh.domain.tool.web.browser.adapter.port.IBrowserUsePort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name = "harness.extensions.browser-use.enabled", havingValue = "true")
public class PlaywrightBrowserUse implements IBrowserUsePort {

    private final String npxCommand;
    private final long timeoutSeconds;
    private final Path workDir;

    public PlaywrightBrowserUse() {
        this("npx", 60L, Path.of(System.getProperty("java.io.tmpdir"), "playwright-dsh"));
    }

    public PlaywrightBrowserUse(@Value("${dsh.browser.npx:npx}") String npxCommand,
                                @Value("${dsh.browser.timeout:60}") long timeoutSeconds,
                                Path workDir) {
        this.npxCommand = npxCommand;
        this.timeoutSeconds = timeoutSeconds;
        this.workDir = workDir;
    }

    @Override
    public void navigate(String url) {
        runScript("navigate", url, null, null);
    }

    @Override
    public void click(String selector) {
        runScript("click", null, selector, null);
    }

    @Override
    public void input(String selector, String text) {
        runScript("input", null, selector, text);
    }

    @Override
    public String screenshot() {
        try {
            Path screenshotFile = workDir.resolve("screenshot.png");
            runScript("screenshot", null, null, screenshotFile.toString());
            return Files.readString(screenshotFile);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read screenshot", e);
        }
    }

    @Override
    public String getText(String selector) {
        Path outputFile = workDir.resolve("text-output.txt");
        runScript("getText", null, selector, outputFile.toString());
        try {
            return Files.readString(outputFile);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read text output", e);
        }
    }

    private void runScript(String action, String url, String selector, String extra) {
        try {
            if (!Files.exists(workDir)) {
                Files.createDirectories(workDir);
            }
            ProcessBuilder pb = new ProcessBuilder(
                    npxCommand, "playwright", "run",
                    "--action", action
            );
            if (url != null) {
                pb.command().add("--url");
                pb.command().add(url);
            }
            if (selector != null) {
                pb.command().add("--selector");
                pb.command().add(selector);
            }
            if (extra != null) {
                pb.command().add("--extra");
                pb.command().add(extra);
            }
            pb.directory(workDir.toFile());
            pb.redirectErrorStream(true);
            Process process = pb.start();
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException("playwright command timed out");
            }
            int exitCode = process.exitValue();
            if (exitCode != 0) {
                String output = new String(process.getInputStream().readAllBytes());
                throw new IllegalStateException("playwright failed: " + action + " -> " + output);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to run playwright", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("playwright interrupted", e);
        }
    }
}
