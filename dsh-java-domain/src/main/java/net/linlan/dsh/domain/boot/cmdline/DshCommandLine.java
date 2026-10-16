package net.linlan.dsh.domain.boot.cmdline;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * DshCommandLine 持有应用自己的 flag、--help 与退出码。
 *
 * <p>启动器自身 flag 之后的一切原样透传至应用主逻辑。</p>
 *
 */
public class DshCommandLine {

    private final List<String> appFlags = new ArrayList<>();
    private final List<String> passthroughArgs = new ArrayList<>();
    private boolean helpRequested = false;
    private int exitCode = 0;

    public DshCommandLine parse(String[] args) {
        Objects.requireNonNull(args, "args 不能为空");
        appFlags.clear();
        passthroughArgs.clear();
        helpRequested = false;
        exitCode = 0;

        boolean separatorFound = false;
        for (String arg : args) {
            if (!separatorFound) {
                if ("--help".equals(arg) || "-h".equals(arg)) {
                    helpRequested = true;
                    appFlags.add(arg);
                    continue;
                }
                if ("--".equals(arg)) {
                    separatorFound = true;
                    continue;
                }
                if (arg.startsWith("--dsh.")) {
                    appFlags.add(arg);
                    continue;
                }
                separatorFound = true;
                passthroughArgs.add(arg);
            } else {
                passthroughArgs.add(arg);
            }
        }
        return this;
    }

    public List<String> appFlags() {
        return List.copyOf(appFlags);
    }

    public List<String> passthroughArgs() {
        return List.copyOf(passthroughArgs);
    }

    public boolean isHelpRequested() {
        return helpRequested;
    }

    public int exitCode() {
        return exitCode;
    }

    public void setExitCode(int code) {
        this.exitCode = code;
    }

    public String helpText() {
        return """
                DeepSeek Harness Java - 启动选项
                  --dsh.<key>=<value>   应用 flag（如 --dsh.profile=prod）
                  --help, -h            显示帮助并退出
                  --                    分隔启动器 flag 与应用参数
                其余参数原样透传至应用主逻辑。
                """;
    }
}
