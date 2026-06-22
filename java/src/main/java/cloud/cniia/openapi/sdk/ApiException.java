package cloud.cniia.openapi.sdk;

import java.util.Map;

/**
 * 业务异常：统一响应信封中 {@code code != 0} 时抛出。
 *
 * <p>携带原始 {@code code}/{@code message}/{@code data} 与原始响应体文本，供调用方判断。
 * 不要穷举 code 写死分支——错误码以服务端为准，可能新增（见 INTERFACES.md §六）。
 */
public class ApiException extends RuntimeException {

    private final long code;
    private final Object data;
    private final String rawBody;

    public ApiException(long code, String message, Object data, String rawBody) {
        super("OpenAPI 业务错误 code=" + code + " message=" + message);
        this.code = code;
        this.data = data;
        this.rawBody = rawBody;
    }

    /** 业务错误码（非 0）。 */
    public long code() {
        return code;
    }

    /** data 字段（可能为 null，或含 missing_fields 等）。 */
    public Object data() {
        return data;
    }

    /** data 转 Map（不是 Map 时返回 null）。 */
    @SuppressWarnings("unchecked")
    public Map<String, Object> dataAsMap() {
        return data instanceof Map ? (Map<String, Object>) data : null;
    }

    /** 原始响应体文本（信封完整 JSON）。 */
    public String rawBody() {
        return rawBody;
    }
}
