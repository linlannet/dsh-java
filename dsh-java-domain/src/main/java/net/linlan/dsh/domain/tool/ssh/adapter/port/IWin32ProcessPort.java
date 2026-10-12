package net.linlan.dsh.domain.tool.ssh.adapter.port;

/**
 * IWin32ProcessPort 定义 Win32 进程组控制端口契约。
 *
 * <p>调用流程：create → assign → terminateAll/isActive。</p>
 *
 */
public interface IWin32ProcessPort {

    /**
     * 创建 Job Object。
     * @return true 表示创建成功。
     */
    boolean create();

    /**
     * 将进程加入 Job Object。
     * @param processHandle 进程句柄。
     * @return true 表示分配成功。
     */
    boolean assign(ProcessHandle processHandle);

    /**
     * 终止 Job Object 中所有进程。
     * @return true 表示终止成功。
     */
    boolean terminateAll();

    /**
     * 判断 Job Object 是否仍然活跃。
     * @return true 表示活跃。
     */
    boolean isActive();
}
