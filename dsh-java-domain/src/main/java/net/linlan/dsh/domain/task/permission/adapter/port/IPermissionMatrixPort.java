package net.linlan.dsh.domain.task.permission.adapter.port;

import net.linlan.dsh.domain.task.permission.model.valobj.PermissionMatrixVO;

/**
 * 权限矩阵端口。
 * <p>
 * 执行流程：
 * 1. 领域层发起权限矩阵读取；
 * 2. 基础设施层加载当前配置；
 * 3. 返回给权限策略服务进行判定。
 *
 * <p>调用流程：提交 → 权限评估 → 审批 → 队列 → 执行 → 状态回写。</p>
 * <p>示例：需要审批的命令先挂起，批准后进入执行队列。</p>
 *
 */
public interface IPermissionMatrixPort {

    /**
     * 读取当前权限矩阵。
     * <p>
     * 执行流程：
     * 1. 从配置或持久化介质加载矩阵；
     * 2. 转换为领域值对象；
     * 3. 返回给领域层使用。
     */
    PermissionMatrixVO load();
}
