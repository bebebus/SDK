package cloud.cniia.openapi.sdk;

import java.time.Duration;

/**
 * 客户端配置。包含商户凭证、基址与超时。
 *
 * <p>密钥分两套：{@code apiSecretPay} 用于 pay 类接口与代收/退款回调；
 * {@code apiSecretPayout} 用于 payout 类接口与代付回调。客户端各方法自动选对密钥。
 *
 * <p>沙箱/测试场景可只配其中一套；缺失的那套在调用对应接口时会抛 {@link IllegalStateException}。
 *
 * <p>用法：
 * <pre>{@code
 * Config cfg = Config.builder()
 *     .environment(Environment.SANDBOX)
 *     .merchantNo("M00000001")
 *     .apiKey("ak_demo_key")
 *     .apiSecretPay("sk_pay_xxx")
 *     .apiSecretPayout("sk_payout_xxx")
 *     .timeout(Duration.ofSeconds(30))
 *     .build();
 * }</pre>
 */
public final class Config {

    private final String baseUrl;
    private final String merchantNo;
    private final String apiKey;
    private final String apiSecretPay;
    private final String apiSecretPayout;
    private final Duration timeout;

    private Config(Builder b) {
        // baseUrl 优先级：显式自定义 > 环境预设；PRODUCTION 无内置基址，必须显式传 baseUrl
        String resolved = (b.baseUrl != null && !b.baseUrl.isEmpty()) ? b.baseUrl
                : (b.environment != null ? b.environment.baseUrl() : null);
        if (resolved == null || resolved.isEmpty()) {
            throw new IllegalArgumentException(
                    "baseUrl is required: obtain the production URL from your service provider "
                            + "(e.g. https://api.<domain>/api/open/v1)");
        }
        // 去掉尾部斜杠，统一后续拼接
        while (resolved.endsWith("/")) {
            resolved = resolved.substring(0, resolved.length() - 1);
        }
        // 传输安全：非 SANDBOX 且非 localhost/127.0.0.1 的基址必须是 https://（本地联调放行 http）。
        requireHttpsUnlessLocal(resolved, b.environment);
        if (b.merchantNo == null || b.merchantNo.isEmpty()) {
            throw new IllegalArgumentException("merchantNo 必填");
        }
        if (b.apiKey == null || b.apiKey.isEmpty()) {
            throw new IllegalArgumentException("apiKey 必填");
        }
        this.baseUrl = resolved;
        this.merchantNo = b.merchantNo;
        this.apiKey = b.apiKey;
        this.apiSecretPay = b.apiSecretPay;
        this.apiSecretPayout = b.apiSecretPayout;
        this.timeout = b.timeout != null ? b.timeout : Duration.ofSeconds(30);
    }

    /**
     * 传输安全校验：除 SANDBOX 环境与本地回环地址外，基址必须是 https://，否则拒绝构造。
     *
     * <p>放行 http 的情形：
     * <ul>
     *   <li>{@code environment == SANDBOX}（内置本地联调地址）；</li>
     *   <li>主机名为 localhost / 127.0.0.1 / ::1（本地联调）。</li>
     * </ul>
     * 其余一律要求 https，防止凭证/签名经明文传输被窃取或篡改。
     */
    private static void requireHttpsUnlessLocal(String url, Environment environment) {
        String lower = url.toLowerCase();
        if (lower.startsWith("https://")) {
            return; // https 始终放行
        }
        if (environment == Environment.SANDBOX) {
            return; // 沙箱内置本地地址放行
        }
        if (isLocalHttp(lower)) {
            return; // 本地回环放行 http，兼容本地联调
        }
        throw new IllegalArgumentException(
                "baseUrl 必须使用 https://（仅 localhost/127.0.0.1 或 SANDBOX 环境允许 http），收到: " + url);
    }

    /** 判断是否为本地回环的 http 地址（localhost / 127.0.0.1 / ::1）。 */
    private static boolean isLocalHttp(String lowerUrl) {
        if (!lowerUrl.startsWith("http://")) {
            return false;
        }
        // 取 http:// 之后到下一个 '/'、':'、'?'、'#' 之前的主机部分
        String rest = lowerUrl.substring("http://".length());
        int end = rest.length();
        for (int i = 0; i < rest.length(); i++) {
            char c = rest.charAt(i);
            if (c == '/' || c == ':' || c == '?' || c == '#') {
                end = i;
                break;
            }
        }
        String host = rest.substring(0, end);
        // 处理 IPv6 字面量括号 [::1]
        if (host.startsWith("[") && host.endsWith("]")) {
            host = host.substring(1, host.length() - 1);
        }
        return host.equals("localhost") || host.equals("127.0.0.1") || host.equals("::1");
    }

    public String baseUrl() { return baseUrl; }
    public String merchantNo() { return merchantNo; }
    public String apiKey() { return apiKey; }
    public Duration timeout() { return timeout; }

    /** 取代收/退款密钥（pay 类）。未配置则抛异常。 */
    public String requireSecretPay() {
        if (apiSecretPay == null || apiSecretPay.isEmpty()) {
            throw new IllegalStateException("未配置 apiSecretPay，无法调用 pay 类接口或验签代收回调");
        }
        return apiSecretPay;
    }

    /** 取代付密钥（payout 类）。未配置则抛异常。 */
    public String requireSecretPayout() {
        if (apiSecretPayout == null || apiSecretPayout.isEmpty()) {
            throw new IllegalStateException("未配置 apiSecretPayout，无法调用 payout 类接口或验签代付回调");
        }
        return apiSecretPayout;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** 配置构建器。 */
    public static final class Builder {
        private String baseUrl;
        private Environment environment;
        private String merchantNo;
        private String apiKey;
        private String apiSecretPay;
        private String apiSecretPayout;
        private Duration timeout;

        /** 选预设环境（PRODUCTION/SANDBOX）。 */
        public Builder environment(Environment environment) {
            this.environment = environment;
            return this;
        }

        /** 显式自定义基址（覆盖 environment，用于服务商提供的地址或自定义端口）。 */
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder merchantNo(String merchantNo) {
            this.merchantNo = merchantNo;
            return this;
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /** 代收/退款密钥。 */
        public Builder apiSecretPay(String apiSecretPay) {
            this.apiSecretPay = apiSecretPay;
            return this;
        }

        /** 代付密钥。 */
        public Builder apiSecretPayout(String apiSecretPayout) {
            this.apiSecretPayout = apiSecretPayout;
            return this;
        }

        /** HTTP 超时（默认 30s）。 */
        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Config build() {
            return new Config(this);
        }
    }
}
