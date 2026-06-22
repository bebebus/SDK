import cloud.cniia.openapi.sdk.ApiException;
import cloud.cniia.openapi.sdk.ApiResponse;
import cloud.cniia.openapi.sdk.Client;
import cloud.cniia.openapi.sdk.Config;
import cloud.cniia.openapi.sdk.Environment;
import cloud.cniia.openapi.sdk.TransportException;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 代收下单示例。
 *
 * <p>编译运行（在 java 目录下）：
 * <pre>{@code
 * javac --release 17 -d out $(find src/main/java -name '*.java') examples/PayCreateExample.java
 * java -cp out PayCreateExample
 * }</pre>
 */
public class PayCreateExample {

    public static void main(String[] args) {
        // 沙箱环境（亦可 .baseUrl("https://api.<agent_domain>/api/open/v1") 用代理专有域名覆盖）
        Config config = Config.builder()
                .environment(Environment.SANDBOX)
                .merchantNo("M00000001")
                .apiKey("ak_demo_key")
                .apiSecretPay("sk_test_0123456789abcdef0123456789abcdef")
                .apiSecretPayout("sk_payout_demo")
                .timeout(Duration.ofSeconds(30))
                .build();

        Client client = new Client(config);

        // 业务字段：值为 null 的会被自动跳过（不发送、不签名）
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("out_order_no", "202501010001");
        params.put("amount", 10000L);          // 最小单位整数：10000 = 1 元
        params.put("currency", "PHP");
        params.put("pay_method", "gcash");
        params.put("country", "PH");
        params.put("notify_url", "https://merchant.example.com/api/notify/pay");
        params.put("subject", "订单/支付 <A&B>");

        // extra 嵌套对象会按稳定 JSON 序列化参与签名
        Map<String, Object> customer = new LinkedHashMap<>();
        customer.put("first_name", "San");
        customer.put("last_name", "Zhang");
        customer.put("email", "san@example.com");
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("customer", customer);
        params.put("extra", extra);

        try {
            ApiResponse resp = client.payCreate(params);
            Map<String, Object> data = resp.dataAsMap();
            System.out.println("下单成功:");
            System.out.println("  order_no   = " + data.get("order_no"));
            System.out.println("  status     = " + data.get("status"));
            System.out.println("  pay_url    = " + data.get("pay_url"));
            System.out.println("  raw        = " + resp.rawBody());
        } catch (ApiException e) {
            // 业务码错误（code != 0）
            System.out.println("业务失败 code=" + e.code() + " message=" + e.getMessage());
            System.out.println("data=" + e.data());
        } catch (TransportException e) {
            // 网络/HTTP 错误
            System.out.println("传输失败: " + e.getMessage() + " status=" + e.statusCode());
        }
    }
}
