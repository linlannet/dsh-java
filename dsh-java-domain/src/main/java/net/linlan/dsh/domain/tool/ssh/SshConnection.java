package net.linlan.dsh.domain.tool.ssh;

import net.linlan.dsh.domain.tool.ssh.adapter.port.ISshPort;

/**
 * SshConnection 记录 SSH 连接信息并提供连接/断开/执行能力。
 *
 * <p>调用流程：构造连接信息 → connect → executeCommand → disconnect。</p>
 *
 */
public record SshConnection(String host, int port, String username, String keyPath) {

    private static final int DEFAULT_PORT = 22;

    /**
     * 紧凑构造，校验必填字段。
     * @param host     主机。
     * @param port     端口。
     * @param username 用户名。
     * @param keyPath  私钥路径。
     */
    public SshConnection {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("host must not be blank");
        }
        if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException("port must be in 1..65535");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username must not be blank");
        }
    }

    /**
     * 简化构造，使用默认端口 22。
     * @param host     主机。
     * @param username 用户名。
     * @param keyPath  私钥路径。
     * @return SshConnection 实例。
     */
    public static SshConnection of(String host, String username, String keyPath) {
        return new SshConnection(host, DEFAULT_PORT, username, keyPath);
    }

    /**
     * 通过端口建立连接。
     * @param port ISshPort 实现。
     * @return true 表示连接成功。
     */
    public boolean connect(ISshPort port) {
        return port != null && port.connect();
    }

    /**
     * 通过端口断开连接。
     * @param port ISshPort 实现。
     */
    public void disconnect(ISshPort port) {
        if (port != null) {
            port.disconnect();
        }
    }

    /**
     * 通过端口执行远程命令。
     * @param port    ISshPort 实现。
     * @param command 命令。
     * @return 执行结果。
     */
    public ISshPort.SshCommandResult executeCommand(ISshPort port, String command) {
        if (port == null) {
            throw new IllegalArgumentException("port must not be null");
        }
        return port.execute(command);
    }

    /**
     * 构建 ssh 命令行前缀。
     * @return ssh 命令行字符串。
     */
    public String toCommandLine() {
        StringBuilder sb = new StringBuilder("ssh");
        if (port != DEFAULT_PORT) {
            sb.append(" -p ").append(port);
        }
        if (keyPath != null && !keyPath.isBlank()) {
            sb.append(" -i ").append(keyPath);
        }
        sb.append(" ").append(username).append("@").append(host);
        return sb.toString();
    }
}
