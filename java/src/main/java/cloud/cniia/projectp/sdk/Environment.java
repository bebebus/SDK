package cloud.cniia.projectp.sdk;

/**
 * 预设环境基址。
 *
 * <ul>
 *   <li>{@link #PRODUCTION} — 文档默认正式地址；真实正式地址按上级代理专有域名派生，可用自定义 baseUrl 覆盖。</li>
 *   <li>{@link #SANDBOX} — 本地/联调地址。</li>
 * </ul>
 *
 * <p>需要代理专有域名或自定义端口时，用 {@link Config.Builder#baseUrl(String)} 显式覆盖。
 */
public enum Environment {
    PRODUCTION("https://api.project-p-merchant.cniia.cloud/api/open/v1"),
    SANDBOX("http://127.0.0.1:3090/api/open/v1");

    private final String baseUrl;

    Environment(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    /** 该环境的基址（无尾斜杠）。 */
    public String baseUrl() {
        return baseUrl;
    }
}
