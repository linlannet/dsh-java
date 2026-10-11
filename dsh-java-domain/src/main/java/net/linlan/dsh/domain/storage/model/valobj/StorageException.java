package net.linlan.dsh.domain.storage.model.valobj;

/**
 * StorageException 定义当前模块中的class契约与职责。
 * <p>调用流程：应用领域规则 → 更新聚合或值对象 → 发布事件/返回结果。</p>
 * <p>示例：领域对象只暴露业务语言方法，不直接依赖基础设施。</p>
 */
public class StorageException extends RuntimeException {

    private final StorageErrorCode code;

    /**
     * 处理Storage Exception。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param code 编码参数。
     * @param message 消息内容或对象。
     * @return 方法执行结果。
     */
    public StorageException(StorageErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 处理Storage Exception。
     * 流程：应用领域规则 → 更新领域对象 → 返回结果。
     * @param code 编码参数。
     * @param message 消息内容或对象。
     * @param cause 取消原因。
     * @return 方法执行结果。
     */
    public StorageException(StorageErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    /**
     * 处理编码。
     * @return 方法执行结果。
     */
    public StorageErrorCode code() {
        return code;
    }
}
