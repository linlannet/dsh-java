package net.linlan.dsh.api.response;

/**
 * 统一响应包装。
 *
 * @param code 业务状态码
 * @param info 状态说明
 * @param data 业务数据
 * @param <T> 业务数据类型
 */
public record Response<T>(String code, String info, T data) {

    public static final String SUCCESS_CODE = "00000";
    public static final String INVALID_ARGUMENT_CODE = "40000";
    public static final String INTERNAL_ERROR_CODE = "50000";

    public static <T> Response<T> success(T data) {
        return new Response<>(SUCCESS_CODE, "success", data);
    }

    public static <T> Response<T> fail(String code, String info) {
        return new Response<>(code, info, null);
    }

    public static <T> Response<T> invalidArgument(String info) {
        return fail(INVALID_ARGUMENT_CODE, info);
    }

    public static <T> Response<T> internalError(String info) {
        return fail(INTERNAL_ERROR_CODE, info);
    }
}
