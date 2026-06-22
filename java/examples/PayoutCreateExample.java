import cloud.cniia.openapi.sdk.ApiException;
import cloud.cniia.openapi.sdk.ApiResponse;
import cloud.cniia.openapi.sdk.Client;
import cloud.cniia.openapi.sdk.Config;
import cloud.cniia.openapi.sdk.Environment;
import cloud.cniia.openapi.sdk.TransportException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 代付下单示例（银行类）。先查银行编码，再下单。
 *
 * <p>编译运行（在 java 目录下）：
 * <pre>{@code
 * javac --release 17 -d out $(find src/main/java -name '*.java') examples/PayoutCreateExample.java
 * java -cp out PayoutCreateExample
 * }</pre>
 */
public class PayoutCreateExample {

    public static void main(String[] args) {
        Config config = Config.builder()
                .environment(Environment.SANDBOX)
                .merchantNo("M00000001")
                .apiKey("ak_demo_key")
                .apiSecretPay("sk_pay_demo")
                .apiSecretPayout("sk_test_0123456789abcdef0123456789abcdef")
                .build();

        Client client = new Client(config);

        try {
            // 1) 查可用银行（银行类必须传 bank_code，取自此处 code）
            Map<String, Object> banksReq = new LinkedHashMap<>();
            banksReq.put("pay_method", "bank");
            banksReq.put("country", "PH");
            ApiResponse banks = client.payoutBanksQuery(banksReq);
            System.out.println("可用银行: " + banks.dataAsMap());

            // 2) 代付下单
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("out_payout_no", "WD202501010001");
            params.put("amount", 100000L);     // 10 元
            params.put("currency", "PHP");
            params.put("pay_method", "bank");
            params.put("country", "PH");
            params.put("notify_url", "https://merchant.example.com/api/notify/payout");
            params.put("account_no", "1234567890");
            params.put("account_name", "San Zhang");
            params.put("bank_code", "BDO");

            ApiResponse resp = client.payoutCreate(params);
            Map<String, Object> data = resp.dataAsMap();
            System.out.println("代付受理成功:");
            System.out.println("  payout_no     = " + data.get("payout_no"));
            System.out.println("  status        = " + data.get("status"));
            System.out.println("  freeze_amount = " + data.get("freeze_amount"));
            System.out.println("  最终结果以异步回调与查单为准");
        } catch (ApiException e) {
            System.out.println("业务失败 code=" + e.code() + " message=" + e.getMessage());
            System.out.println("data=" + e.data());
        } catch (TransportException e) {
            System.out.println("传输失败: " + e.getMessage());
        }
    }
}
