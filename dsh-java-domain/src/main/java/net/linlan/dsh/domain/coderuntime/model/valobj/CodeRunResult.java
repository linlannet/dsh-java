package net.linlan.dsh.domain.coderuntime.model.valobj;

/**
 * 代码执行结果。
 * <p>
 * 执行流程：
 * 1. 代码运行结束后生成结果；
 * 2. 记录成功状态、输出和错误信息；
 * 3. 返回给上层展示或继续处理。
 *
 * @param success 是否执行成功
 * @param output 标准输出内容
 * @param error 失败时的错误信息
 * @param exitCode 进程退出码
 *
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 *
 */
public record CodeRunResult(boolean success, String output, String error, Integer exitCode) {
    /**
     * 处理ok。
     * @param output 输出内容。
     * @return 方法执行结果。
     */
    public static CodeRunResult ok(String output) {
        return new CodeRunResult(true, output, null, 0);
    }
    /**
     * 处理fail。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param error error参数。
     * @param exitCode exit 编码参数。
     * @return 方法执行结果。
     */
    public static CodeRunResult fail(String error, Integer exitCode) {
        return new CodeRunResult(false, null, error, exitCode);
    }
}
