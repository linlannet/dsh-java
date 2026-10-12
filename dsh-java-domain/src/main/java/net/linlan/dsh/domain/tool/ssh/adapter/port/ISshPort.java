package net.linlan.dsh.domain.tool.ssh.adapter.port;

/**
 * ISshPort 定义 SSH 连接端口契约。
 *
 * <p>调用流程：connect → execute/readFile/writeFile → disconnect。</p>
 *
 */
public interface ISshPort {

    /**
     * 建立连接。
     * @return true 表示连接成功。
     */
    boolean connect();

    /**
     * 断开连接。
     */
    void disconnect();

    /**
     * 执行远程命令。
     * @param command 命令。
     * @return 执行结果（stdout + stderr + exitCode）。
     */
    SshCommandResult execute(String command);

    /**
     * 读取远程文件。
     * @param remotePath 远程路径。
     * @return 文件内容。
     */
    String readFile(String remotePath);

    /**
     * 写入远程文件。
     * @param remotePath 远程路径。
     * @param content    内容。
     * @return true 表示写入成功。
     */
    boolean writeFile(String remotePath, String content);

    /**
     * SshCommandResult 记录命令执行结果。
     */
    record SshCommandResult(String stdout, String stderr, int exitCode) {
        /**
         * 判断是否成功。
         * @return true 表示退出码为 0。
         */
        public boolean isSuccess() {
            return exitCode == 0;
        }
    }
}
