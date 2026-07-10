import cloud.cniia.openapi.sdk.Client;
import cloud.cniia.openapi.sdk.Config;
import cloud.cniia.openapi.sdk.Environment;
import cloud.cniia.openapi.sdk.Json;
import cloud.cniia.openapi.sdk.Signer;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 回调验签 + 处理代码片段（不是常驻 HTTP 服务）。
 *
 * <p>演示两类回调各一次：
 * <ul>
 *   <li>代收回调 → 用 api_secret_pay 验签</li>
 *   <li>代付回调 → 用 api_secret_payout 验签</li>
 * </ul>
 *
 * <p>处理流程：拿到原始 body → Json.parse → verifyCallback（时序安全）→ 按 status 幂等处理
 * （success / failed 分支）→ 正确应答（HTTP 200 + 纯文本 success）。
 *
 * <p>在真实 Web 框架里，把 {@code handleCallback} 接到 HTTP handler：原始请求体作为 rawBody，
 * 返回值作为响应体（Content-Type: text/plain，状态码 200）。验签失败务必返回非 success 体或非 2xx，
 * 同一订单可能再次收到回调。
 *
 * <p>编译运行（在 java 目录下）：
 * <pre>{@code
 * javac --release 17 -d out $(find src/main/java -name '*.java') examples/CallbackVerifyExample.java
 * java -cp out CallbackVerifyExample
 * }</pre>
 */
public class CallbackVerifyExample {

    // 两套回调密钥
    static final String API_SECRET_PAY = "sk_pay_secret_demo";
    static final String API_SECRET_PAYOUT = "sk_payout_secret_demo";

    public static void main(String[] args) {
        Config config = Config.builder()
                .environment(Environment.PRODUCTION)
                // PRODUCTION 无内置基址：正式地址请向服务商获取，必须显式提供
                .baseUrl("https://api.<service_domain>/api/open/v1")
                .merchantNo("M00000001")
                .apiKey("ak_demo_key")
                .apiSecretPay(API_SECRET_PAY)
                .apiSecretPayout(API_SECRET_PAYOUT)
                .build();
        Client client = new Client(config);

        // ===== 1) 代收回调 =====
        // 模拟收到的原始回调 body（实际签名由服务计算）。
        String payRawBody = buildSignedBody(payCallbackFields(), API_SECRET_PAY);
        System.out.println("代收回调原始 body:\n  " + payRawBody);
        String payAck = handlePayCallback(client, payRawBody);
        System.out.println("代收回调应答: " + payAck + "\n");

        // ===== 2) 代付回调 =====
        String payoutRawBody = buildSignedBody(payoutCallbackFields(), API_SECRET_PAYOUT);
        System.out.println("代付回调原始 body:\n  " + payoutRawBody);
        String payoutAck = handlePayoutCallback(client, payoutRawBody);
        System.out.println("代付回调应答: " + payoutAck + "\n");

        // ===== 3) 篡改演示：验签失败必须不回 success =====
        String tampered = payRawBody.replace("\"amount\":10000", "\"amount\":99999");
        String tamperedAck = handlePayCallback(client, tampered);
        System.out.println("被篡改的代收回调应答: " + tamperedAck + " （非 success，同一订单可能再次收到回调）");
    }

    /** 处理代收回调：用 api_secret_pay 验签。返回回调应答体。 */
    static String handlePayCallback(Client client, String rawBody) {
        Map<String, Object> payload;
        try {
            payload = Json.parseObject(rawBody);
        } catch (RuntimeException e) {
            return "invalid"; // 非 JSON：拒绝；同一订单可能再次收到回调
        }
        // 时序安全验签（密钥自动选 pay）
        if (!client.verifyPayCallback(payload)) {
            return "verify_failed"; // 非 success；同一订单可能再次收到回调
        }
        String orderNo = String.valueOf(payload.get("order_no"));
        String status = String.valueOf(payload.get("status"));
        // 幂等处理：同一订单可能被回调多次，先查本地状态再决定是否入账
        if ("success".equals(status)) {
            // markPaidIdempotent(orderNo, payload.get("actual_amount"));
            System.out.println("  [代收] 订单 " + orderNo + " 支付成功，幂等入账");
        } else if ("failed".equals(status)) {
            // markFailedIdempotent(orderNo);
            System.out.println("  [代收] 订单 " + orderNo + " 支付失败，幂等标记");
        } else {
            System.out.println("  [代收] 订单 " + orderNo + " 状态 " + status + "，按业务处理");
        }
        return "success"; // HTTP 200 + 纯文本 success
    }

    /** 处理代付回调：用 api_secret_payout 验签。 */
    static String handlePayoutCallback(Client client, String rawBody) {
        Map<String, Object> payload;
        try {
            payload = Json.parseObject(rawBody);
        } catch (RuntimeException e) {
            return "invalid";
        }
        if (!client.verifyPayoutCallback(payload)) {
            return "verify_failed";
        }
        String payoutNo = String.valueOf(payload.get("payout_no"));
        String status = String.valueOf(payload.get("status"));
        if ("success".equals(status)) {
            System.out.println("  [代付] 出款单 " + payoutNo + " 成功，幂等确认");
        } else if ("failed".equals(status)) {
            // 失败通常已解冻，按业务对账
            System.out.println("  [代付] 出款单 " + payoutNo + " 失败，原因=" + payload.get("failed_reason"));
        } else {
            System.out.println("  [代付] 出款单 " + payoutNo + " 状态 " + status);
        }
        return "success";
    }

    // ---------- 仅用于本示例：本地按签名规则造一个带 sign 的回调体 ----------

    private static Map<String, Object> payCallbackFields() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("merchant_no", "M00000001");
        m.put("order_no", "P202501010001");
        m.put("out_order_no", "202501010001");
        m.put("amount", 10000L);
        m.put("actual_amount", 10000L);
        m.put("fee_amount", 180L);
        m.put("net_amount", 9820L);
        m.put("currency", "PHP");
        m.put("status", "success");
        m.put("channel_order_no", null); // 恒 null，不参与签名
        m.put("paid_at", "2025-01-01T08:00:00+08:00");
        return m;
    }

    private static Map<String, Object> payoutCallbackFields() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("merchant_no", "M00000001");
        m.put("payout_no", "WD202501010001");
        m.put("out_payout_no", "OW202501010001");
        m.put("amount", 100000L);
        m.put("currency", "PHP");
        m.put("status", "success");
        m.put("fee_amount", 500L);
        m.put("channel_order_no", null);
        m.put("finished_at", "2025-01-01T09:00:00+08:00");
        return m;
    }

    private static String buildSignedBody(Map<String, Object> fields, String secret) {
        String sign = Signer.sign(fields, secret);
        Map<String, Object> withSign = new LinkedHashMap<>(fields);
        withSign.remove("channel_order_no"); // null 不放入 body
        withSign.put("sign", sign);
        return Json.serialize(withSign);
    }
}
