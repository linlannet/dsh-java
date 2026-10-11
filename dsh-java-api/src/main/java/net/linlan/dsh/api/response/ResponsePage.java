package net.linlan.dsh.api.response;

/**
 * 统一分页响应包装。
 *
 * @param code 业务状态码
 * @param info 状态说明
 * @param data 分页数据
 * @param total 总记录数
 * @param <T> 分页数据类型
 */
public record ResponsePage<T>(String code, String info, T data, Long total) {

    public static <T> ResponsePage<T> success(T data, Long total) {
        return new ResponsePage<>("0", "success", data, total);
    }

    public static <T> ResponsePage<T> fail(String code, String info) {
        return new ResponsePage<>(code, info, null, null);
    }
}
