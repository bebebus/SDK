package cloud.cniia.openapi.sdk;

/**
 * 预设环境。
 *
 * <ul>
 *   <li>{@link #PRODUCTION} — 正式环境，<b>无内置基址</b>。正式地址按上级代理专有域名派生，
 *       必须通过 {@link Config.Builder#baseUrl(String)} 显式提供
 *       （形如 {@code https://api.<agent_domain>/api/open/v1}）。</li>
 *   <li>{@link #SANDBOX} — 本地/联调地址。</li>
 * </ul>
 *
 * <p>选 PRODUCTION 时必须显式传 baseUrl；选 SANDBOX 时使用内置本地地址。
 */
public enum Environment {
    PRODUCTION(null),
    SANDBOX("http://127.0.0.1:3090/api/open/v1");

    private final String baseUrl;

    Environment(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    /** 该环境的内置基址（无尾斜杠）；PRODUCTION 无内置基址，返回 {@code null}。 */
    public String baseUrl() {
        return baseUrl;
    }
}
