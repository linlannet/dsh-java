package net.linlan.dsh.domain.tool.ssh;

import net.linlan.dsh.domain.tool.ssh.adapter.port.ISshPort;

import java.util.ArrayList;
import java.util.List;

/**
 * FsOverSsh 通过 SSH 实现远程文件读写与列表。
 *
 * <p>调用流程：read/write/list 委托 ISshPort 执行远程命令。</p>
 *
 */
public final class FsOverSsh {

    private final ISshPort port;

    /**
     * 构造。
     * @param port ISshPort 实现。
     */
    public FsOverSsh(ISshPort port) {
        if (port == null) {
            throw new IllegalArgumentException("port must not be null");
        }
        this.port = port;
    }

    /**
     * 读取远程文件。
     * @param remotePath 远程路径。
     * @return 文件内容。
     */
    public String read(String remotePath) {
        return port.readFile(remotePath);
    }

    /**
     * 写入远程文件。
     * @param remotePath 远程路径。
     * @param content    内容。
     * @return true 表示写入成功。
     */
    public boolean write(String remotePath, String content) {
        return port.writeFile(remotePath, content);
    }

    /**
     * 列出远程目录。
     * @param remoteDir 远程目录。
     * @return 条目列表。
     */
    public List<String> list(String remoteDir) {
        ISshPort.SshCommandResult result = port.execute("ls -1 " + shellQuote(remoteDir));
        if (!result.isSuccess()) {
            return List.of();
        }
        List<String> entries = new ArrayList<>();
        String stdout = result.stdout();
        if (stdout != null && !stdout.isBlank()) {
            for (String line : stdout.split("\n")) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    entries.add(trimmed);
                }
            }
        }
        return entries;
    }

    /**
     * 判断远程文件是否存在。
     * @param remotePath 远程路径。
     * @return true 表示存在。
     */
    public boolean exists(String remotePath) {
        ISshPort.SshCommandResult result = port.execute("test -e " + shellQuote(remotePath) + " && echo yes || echo no");
        return result.isSuccess() && "yes".equals(result.stdout().trim());
    }

    private static String shellQuote(String value) {
        if (value == null) {
            return "''";
        }
        return "'" + value.replace("'", "'\\''") + "'";
    }
}
