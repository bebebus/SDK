package cloud.cniia.openapi.sdk;

/**
 * 传输异常：HTTP/网络错误、超时、非 2xx 状态、响应体非 JSON 等。
 *
 * <p>与 {@link ApiException}（业务码错误）区分：传输层失败抛此异常。
 * 若服务端返回非 2xx，{@link #statusCode()} 为该状态码；纯网络错误（连接失败/超时）时为 -1。
 */
public class TransportException extends RuntimeException {

    private final int statusCode;
    private final String rawBody;

    public TransportException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = -1;
        this.rawBody = null;
    }

    public TransportException(String message, int statusCode, String rawBody) {
        super(message);
        this.statusCode = statusCode;
        this.rawBody = rawBody;
    }

    /** HTTP 状态码；纯网络错误为 -1。 */
    public int statusCode() {
        return statusCode;
    }

    /** 原始响应体（若有）。 */
    public String rawBody() {
        return rawBody;
    }
}
