package cloud.cniia.projectp.sdk;

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
        // baseUrl 优先级：显式自定义 > 环境预设
        String resolved = b.baseUrl != null ? b.baseUrl
                : (b.environment != null ? b.environment.baseUrl() : null);
        if (resolved == null) {
            throw new IllegalArgumentException("必须设置 environment 或 baseUrl 之一");
        }
        // 去掉尾部斜杠，统一后续拼接
        while (resolved.endsWith("/")) {
            resolved = resolved.substring(0, resolved.length() - 1);
        }
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

        /** 显式自定义基址（覆盖 environment，用于代理专有域名或自定义端口）。 */
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
