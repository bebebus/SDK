package cloud.cniia.openapi.sdk;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 商户支付 OpenAPI 客户端，覆盖全部 11 个端点。
 *
 * <p>每个请求自动注入通用字段（merchant_no / api_key / timestamp / nonce），按密钥归属选 pay/payout
 * 密钥计算 sign，POST {@code application/json}，解析统一信封；{@code code != 0} 抛 {@link ApiException}，
 * HTTP/网络层错误抛 {@link TransportException}。
 *
 * <p>线程安全：内部 {@link HttpClient} 可复用，本类无可变状态。
 */
public final class Client {

    /** 密钥归属：决定用 pay 还是 payout 密钥签名。 */
    private enum Secret { PAY, PAYOUT }

    /**
     * [L19] SDK 版本号单一事实源：UA 由此常量派生（不再硬编码）。
     * release.sh 发版时按此常量 sed 同步（与 pom.xml &lt;version&gt; 一致）。
     */
    public static final String VERSION = "1.1.2";

    private final Config config;
    private final HttpClient http;
    private static final SecureRandom RANDOM = new SecureRandom();

    public Client(Config config) {
        this.config = config;
        // HttpClient 默认 Redirect.NEVER（不自动跟随重定向），避免被 3xx 重定向到非 https
        // 或跨域端点泄露签名/凭证；此处不改变默认即为安全行为。
        this.http = HttpClient.newBuilder()
                .connectTimeout(config.timeout())
                .build();
    }

    public Config config() {
        return config;
    }

    // ============================================================
    // 代收（Pay）
    // ============================================================

    /**
     * 代收下单 POST /merchant/pay/create（密钥：pay）。
     * @param params 业务字段：out_order_no, amount(int), currency, pay_method, notify_url 必填；
     *               country, return_url, subject, remark, client_ip, extra 可选；值为 null 的字段不发送也不签名。
     */
    public ApiResponse payCreate(Map<String, Object> params) {
        return call("/merchant/pay/create", params, Secret.PAY);
    }

    /** 代收查单 POST /merchant/pay/query（密钥：pay）。params: order_no 或 out_order_no 二选一。 */
    public ApiResponse payQuery(Map<String, Object> params) {
        return call("/merchant/pay/query", params, Secret.PAY);
    }

    /** 可用支付方式 POST /merchant/pay-methods/query（密钥：pay）。params: country 可选。 */
    public ApiResponse payMethodsQuery(Map<String, Object> params) {
        return call("/merchant/pay-methods/query", params, Secret.PAY);
    }

    /** 余额查询 POST /merchant/balance/query（密钥：pay）。params: currency 可选。 */
    public ApiResponse balanceQuery(Map<String, Object> params) {
        return call("/merchant/balance/query", params, Secret.PAY);
    }

    /**
     * 代收测试单完成 POST /merchant/pay/test/complete（密钥：pay，仅测试密钥）。
     * params: order_no 或 out_order_no 二选一 + result(success|failed) + actual_amount(int 可选)。
     */
    public ApiResponse payTestComplete(Map<String, Object> params) {
        return call("/merchant/pay/test/complete", params, Secret.PAY);
    }

    // ============================================================
    // 代付（Payout）
    // ============================================================

    /**
     * 代付下单 POST /merchant/payout/create（密钥：payout）。
     * params: out_payout_no, amount(int), currency, pay_method, notify_url, account_no 必填；
     *         country, account_name, bank_code, bank_name, remark, client_ip, extra 可选。
     */
    public ApiResponse payoutCreate(Map<String, Object> params) {
        return call("/merchant/payout/create", params, Secret.PAYOUT);
    }

    /** 代付查单 POST /merchant/payout/query（密钥：payout）。params: payout_no 或 out_payout_no 二选一。 */
    public ApiResponse payoutQuery(Map<String, Object> params) {
        return call("/merchant/payout/query", params, Secret.PAYOUT);
    }

    /**
     * 可用银行 POST /merchant/payout/banks/query（密钥：payout）。
     * params: pay_method 必填 + country(法币必填) + currency 可选。
     */
    public ApiResponse payoutBanksQuery(Map<String, Object> params) {
        return call("/merchant/payout/banks/query", params, Secret.PAYOUT);
    }

    /** 代付凭证查询 POST /merchant/payout/proof/query（密钥：payout）。params: payout_no 或 out_payout_no 二选一。 */
    public ApiResponse payoutProofQuery(Map<String, Object> params) {
        return call("/merchant/payout/proof/query", params, Secret.PAYOUT);
    }

    /**
     * 代付收据 POST /merchant/payout/receipt/query（密钥：payout）。
     * params: payout_no 或 out_payout_no 二选一 + lang(en|zh-CN|zh-TW 可选) + inline(布尔可选)。
     * <p>注意：inline 会被归一为整数 1/0 发送（避免布尔签名歧义）。
     */
    public ApiResponse payoutReceiptQuery(Map<String, Object> params) {
        Map<String, Object> p = new LinkedHashMap<>(params);
        if (p.containsKey("inline")) {
            Object v = p.get("inline");
            if (v != null) {
                p.put("inline", normalizeInline(v));
            }
        }
        return call("/merchant/payout/receipt/query", p, Secret.PAYOUT);
    }

    /**
     * 代付测试单完成 POST /merchant/payout/test/complete（密钥：payout，仅测试密钥）。
     * params: payout_no 或 out_payout_no 二选一 + result(success|failed)。
     */
    public ApiResponse payoutTestComplete(Map<String, Object> params) {
        return call("/merchant/payout/test/complete", params, Secret.PAYOUT);
    }

    // ============================================================
    // 回调验签（不发请求）
    // ============================================================

    /** 验证代收/退款回调（密钥：api_secret_pay）。 */
    public boolean verifyPayCallback(Map<String, Object> payload) {
        return Signer.verifyCallback(payload, config.requireSecretPay());
    }

    /** 验证代付回调（密钥：api_secret_payout）。 */
    public boolean verifyPayoutCallback(Map<String, Object> payload) {
        return Signer.verifyCallback(payload, config.requireSecretPayout());
    }

    // ============================================================
    // 内部：请求构建 + 发送 + 信封解析
    // ============================================================

    /** 发请求并在 code!=0 时抛 {@link ApiException}。 */
    private ApiResponse call(String path, Map<String, Object> params, Secret which) {
        ApiResponse resp = callRaw(path, params, which);
        if (!resp.isSuccess()) {
            throw new ApiException(resp.code(), resp.message(), resp.data(), resp.rawBody());
        }
        return resp;
    }

    /**
     * 发请求并返回信封解析结果（不抛业务异常，供自行判码）。
     * HTTP/网络错误仍抛 {@link TransportException}。
     */
    public ApiResponse callRaw(String path, Map<String, Object> params, boolean payout) {
        return callRaw(path, params, payout ? Secret.PAYOUT : Secret.PAY);
    }

    private ApiResponse callRaw(String path, Map<String, Object> params, Secret which) {
        String secret = which == Secret.PAY ? config.requireSecretPay() : config.requireSecretPayout();

        // 1) 构建带通用字段的有序 payload（key 升序，便于阅读；签名器内部也会再排序）
        Map<String, Object> payload = buildPayload(params);

        // 2) 计算 sign 并放回
        String sign = Signer.sign(payload, secret);
        payload.put("sign", sign);

        // 3) 序列化请求体
        String body = Json.serialize(payload);

        // 4) 发送
        String url = config.baseUrl() + path;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(config.timeout())
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                // 自识别 User-Agent：避免被 WAF/CDN（如 Cloudflare）按默认 UA 拦成 403。
                // 版本号从 VERSION 常量单一派生。
                .header("User-Agent", "openapi-sdk-java/" + VERSION)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new TransportException("HTTP 请求失败: " + url + " — " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TransportException("HTTP 请求被中断: " + url, e);
        }

        int status = response.statusCode();
        String raw = response.body();
        if (status < 200 || status >= 300) {
            throw new TransportException("HTTP 非 2xx 状态 " + status + " for " + url, status, raw);
        }

        // 5) 解析统一信封
        return parseEnvelope(raw);
    }

    /** 注入通用字段、过滤 null，返回 key 升序的有序 payload（不含 sign）。 */
    private Map<String, Object> buildPayload(Map<String, Object> params) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (params != null) {
            for (Map.Entry<String, Object> e : params.entrySet()) {
                if ("sign".equals(e.getKey())) {
                    continue; // 调用方误传 sign 一律忽略
                }
                if (e.getValue() == null) {
                    continue; // null 不入体不签名
                }
                merged.put(e.getKey(), e.getValue());
            }
        }
        // 通用字段由 SDK 统一注入，且**始终覆盖**调用方同名字段（跨语言一致语义）。
        merged.put("merchant_no", config.merchantNo());
        merged.put("api_key", config.apiKey());
        merged.put("timestamp", Instant.now().getEpochSecond());
        merged.put("nonce", generateNonce());
        return merged;
    }

    private ApiResponse parseEnvelope(String raw) {
        Object parsed;
        try {
            parsed = Json.parse(raw);
        } catch (RuntimeException e) {
            throw new TransportException("响应体非合法 JSON: " + truncate(raw), -1, raw);
        }
        if (!(parsed instanceof Map)) {
            throw new TransportException("响应信封不是 JSON object: " + truncate(raw), -1, raw);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> env = (Map<String, Object>) parsed;
        Object codeObj = env.get("code");
        long code;
        if (codeObj == null) {
            // 缺 code 视为传输异常
            throw new TransportException("响应缺少 code 字段: " + truncate(raw), -1, raw);
        } else if (codeObj instanceof Number) {
            // code 仅接受整数：有小数部分按非法信封 fail-closed，杜绝 0.5→0 截断误判成功。
            Number n = (Number) codeObj;
            if (n instanceof BigDecimal) {
                BigDecimal bd = (BigDecimal) n;
                if (bd.stripTrailingZeros().scale() > 0) {
                    throw new TransportException("响应 code 非整数: " + codeObj, -1, raw);
                }
                code = bd.longValueExact();
            } else if (n instanceof Double || n instanceof Float) {
                double d = n.doubleValue();
                if (d != Math.rint(d) || Double.isNaN(d) || Double.isInfinite(d)) {
                    throw new TransportException("响应 code 非整数: " + codeObj, -1, raw);
                }
                code = (long) d;
            } else {
                // Long / Integer / BigInteger 等整数类型直接取整数值
                code = n.longValue();
            }
        } else {
            // 字符串等其他形态：必须是合法整数文本，否则非法信封。
            try {
                code = Long.parseLong(codeObj.toString().trim());
            } catch (NumberFormatException nfe) {
                throw new TransportException("响应 code 非整数: " + codeObj, -1, raw);
            }
        }
        Object msgObj = env.get("message");
        String message = msgObj == null ? null : msgObj.toString();
        Object data = env.get("data");
        return new ApiResponse(code, message, data, raw);
    }

    // ============================================================
    // 工具
    // ============================================================

    /** 生成唯一 nonce：时间戳 + 16 字节随机 hex。 */
    static String generateNonce() {
        byte[] buf = new byte[16];
        RANDOM.nextBytes(buf);
        StringBuilder sb = new StringBuilder(40);
        sb.append(Long.toHexString(System.nanoTime()));
        sb.append('-');
        final char[] HEX = "0123456789abcdef".toCharArray();
        for (byte b : buf) {
            int v = b & 0xFF;
            sb.append(HEX[v >>> 4]).append(HEX[v & 0x0F]);
        }
        return sb.toString();
    }

    /** 把 inline 归一为整数 1/0。接受 Boolean / Number / "1"/"0"/"true"/"false"。 */
    public static long normalizeInline(Object v) {
        if (v instanceof Boolean) {
            return ((Boolean) v) ? 1L : 0L;
        }
        if (v instanceof Number) {
            return ((Number) v).longValue() != 0 ? 1L : 0L;
        }
        String s = v.toString().trim().toLowerCase();
        return ("1".equals(s) || "true".equals(s) || "yes".equals(s)) ? 1L : 0L;
    }

    private static String truncate(String s) {
        if (s == null) {
            return "null";
        }
        return s.length() > 500 ? s.substring(0, 500) + "..." : s;
    }
}
