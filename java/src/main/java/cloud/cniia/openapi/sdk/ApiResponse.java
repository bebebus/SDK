// SPDX-License-Identifier: MIT

package cloud.cniia.openapi.sdk;

import java.util.Map;

/**
 * 统一响应信封解析结果，保留原始途径。
 *
 * <p>{@code code()==0} 为成功。SDK 默认在 {@code code != 0} 时抛 {@link ApiException}；
 * 若调用方想自行判码，可用 {@link Client#callRaw} 拿到本对象（不抛业务异常）。
 */
public final class ApiResponse {

    private final long code;
    private final String message;
    private final Object data;
    private final String rawBody;
    private final String requestSign;
    private final int statusCode;

    ApiResponse(long code, String message, Object data, String rawBody, String requestSign) {
        this(code, message, data, rawBody, requestSign, 200);
    }

    ApiResponse(long code, String message, Object data, String rawBody, String requestSign, int statusCode) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.rawBody = rawBody;
        this.requestSign = requestSign;
        this.statusCode = statusCode;
    }

    /** 业务码（0=成功）。 */
    public long code() {
        return code;
    }

    /** HTTP 状态码（通常为 200；503/500 携带业务错误信封时保留原状态码）。 */
    public int statusCode() {
        return statusCode;
    }

    public String message() {
        return message;
    }

    /** data 字段（object→Map / array→List / 标量 / null）。 */
    public Object data() {
        return data;
    }

    /** data 转 Map（不是 Map 时返回 null）。 */
    @SuppressWarnings("unchecked")
    public Map<String, Object> dataAsMap() {
        return data instanceof Map ? (Map<String, Object>) data : null;
    }

    /** 原始响应体文本。 */
    public String rawBody() {
        return rawBody;
    }

    /** 本次请求实际发出的 sign。代付反查保存的就是代付下单的这一笔。 */
    public String requestSign() {
        return requestSign;
    }

    public boolean isSuccess() {
        return code == 0;
    }
}
