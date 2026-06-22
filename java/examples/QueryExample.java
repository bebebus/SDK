import cloud.cniia.projectp.sdk.ApiException;
import cloud.cniia.projectp.sdk.ApiResponse;
import cloud.cniia.projectp.sdk.Client;
import cloud.cniia.projectp.sdk.Config;
import cloud.cniia.projectp.sdk.Environment;
import cloud.cniia.projectp.sdk.TransportException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 查询类示例：代收查单、余额、支付方式、代付查单、收据（inline 演示）。
 *
 * <p>编译运行（在 java 目录下）：
 * <pre>{@code
 * javac --release 17 -d out $(find src/main/java -name '*.java') examples/QueryExample.java
 * java -cp out QueryExample
 * }</pre>
 */
public class QueryExample {

    public static void main(String[] args) {
        Config config = Config.builder()
                .environment(Environment.SANDBOX)
                .merchantNo("M00000001")
                .apiKey("ak_demo_key")
                .apiSecretPay("sk_pay_demo")
                .apiSecretPayout("sk_payout_demo")
                .build();
        Client client = new Client(config);

        try {
            // 代收查单（order_no 或 out_order_no 二选一）
            Map<String, Object> q = new LinkedHashMap<>();
            q.put("out_order_no", "202501010001");
            ApiResponse payQuery = client.payQuery(q);
            System.out.println("代收查单: " + payQuery.dataAsMap());

            // 余额
            Map<String, Object> bal = new LinkedHashMap<>();
            bal.put("currency", "PHP");
            System.out.println("余额: " + client.balanceQuery(bal).dataAsMap());

            // 可用支付方式
            Map<String, Object> methods = new LinkedHashMap<>();
            methods.put("country", "PH");
            System.out.println("支付方式: " + client.payMethodsQuery(methods).dataAsMap());

            // 代付查单
            Map<String, Object> pq = new LinkedHashMap<>();
            pq.put("out_payout_no", "WD202501010001");
            System.out.println("代付查单: " + client.payoutQuery(pq).dataAsMap());

            // 代付收据：inline 用布尔传入，SDK 自动归一为整数 1/0 再签名发送
            Map<String, Object> receipt = new LinkedHashMap<>();
            receipt.put("out_payout_no", "WD202501010001");
            receipt.put("lang", "zh-CN");
            receipt.put("inline", true); // → 以整数 1 发送
            System.out.println("代付收据: " + client.payoutReceiptQuery(receipt).dataAsMap());
        } catch (ApiException e) {
            System.out.println("业务失败 code=" + e.code() + " message=" + e.getMessage());
        } catch (TransportException e) {
            System.out.println("传输失败: " + e.getMessage());
        }
    }
}
