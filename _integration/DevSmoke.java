// Java SDK × dev 环境联调。凭据从环境变量读取（PP_MNO/PP_KEY/PP_PAY/PP_POUT/PP_BASE）。
// 序列与其余语言 dev_smoke 一致。编译运行见 run-dev-smoke.sh / 报告中的命令。
import cloud.cniia.openapi.sdk.ApiException;
import cloud.cniia.openapi.sdk.ApiResponse;
import cloud.cniia.openapi.sdk.Client;
import cloud.cniia.openapi.sdk.Config;
import cloud.cniia.openapi.sdk.Signer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DevSmoke {
    static int pass = 0, fail = 0;

    static void ok(String name, boolean cond, String extra) {
        if (cond) pass++; else fail++;
        System.out.println((cond ? "✅" : "❌") + " " + name + (extra.isEmpty() ? "" : " | " + extra));
    }

    static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            if (kv[i + 1] != null) m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    public static void main(String[] args) {
        String mno = System.getenv("PP_MNO"), key = System.getenv("PP_KEY"),
                pay = System.getenv("PP_PAY"), pout = System.getenv("PP_POUT"), base = System.getenv("PP_BASE");
        Client client = new Client(Config.builder()
                .merchantNo(mno).apiKey(key).apiSecretPay(pay).apiSecretPayout(pout).baseUrl(base).build());
        String tag = "java-" + (System.currentTimeMillis() / 1000) + "-" + (int) (Math.random() * 9000 + 1000);
        System.out.println("[Java] base=" + base + " merchant=" + mno + " tag=" + tag);

        // 1. pay-methods/query
        try {
            ApiResponse r = client.payMethodsQuery(map("country", "PH"));
            List<?> methods = (List<?>) r.dataAsMap().get("methods");
            StringBuilder sb = new StringBuilder();
            for (Object o : methods) sb.append(((Map<?, ?>) o).get("pay_method")).append(",");
            ok("pay-methods/query", methods != null && !methods.isEmpty(), sb.toString());
        } catch (Exception e) { ok("pay-methods/query", false, e.getClass().getSimpleName() + " " + e.getMessage()); }

        // 2. balance/query
        try {
            ApiResponse r = client.balanceQuery(map());
            Object balances = r.dataAsMap().get("balances");
            ok("balance/query", balances instanceof List, String.valueOf(balances));
        } catch (Exception e) { ok("balance/query", false, e.getClass().getSimpleName() + " " + e.getMessage()); }

        // 3. pay/create
        String outOrderNo = "sdk-" + tag;
        String orderNo = null;
        try {
            ApiResponse r = client.payCreate(map(
                    "out_order_no", outOrderNo, "amount", 10000, "currency", "PHP", "pay_method", "gcash", "country", "PH",
                    "notify_url", "https://merchant.example.com/api/notify/pay",
                    "extra", map("customer", map("first_name", "San", "last_name", "Zhang", "email", "san@example.com", "phone", "09000000000"))));
            orderNo = (String) r.dataAsMap().get("order_no");
            ok("pay/create", orderNo != null, "order_no=" + orderNo + " status=" + r.dataAsMap().get("status")
                    + " pay_url=" + truncate(r.dataAsMap().get("pay_url")));
        } catch (ApiException e) { ok("pay/create", false, "ApiException code=" + e.code() + " " + e.getMessage() + " " + e.data()); }
        catch (Exception e) { ok("pay/create", false, e.getClass().getSimpleName() + " " + e.getMessage()); }

        // 4. pay/query
        try {
            ApiResponse r = client.payQuery(map("out_order_no", outOrderNo));
            ok("pay/query", outOrderNo.equals(r.dataAsMap().get("out_order_no")),
                    "status=" + r.dataAsMap().get("status") + " notify_status=" + r.dataAsMap().get("notify_status"));
        } catch (Exception e) { ok("pay/query", false, e.getClass().getSimpleName() + " " + e.getMessage()); }

        // 5. payout/banks/query
        String bankCode = null;
        try {
            ApiResponse r = client.payoutBanksQuery(map("pay_method", "bank", "country", "PH", "currency", "PHP"));
            List<?> banks = (List<?>) r.dataAsMap().get("banks");
            if (banks != null && !banks.isEmpty()) bankCode = (String) ((Map<?, ?>) banks.get(0)).get("code");
            ok("payout/banks/query", banks != null, "count=" + (banks == null ? 0 : banks.size()) + " first=" + (bankCode == null ? "N/A" : bankCode));
        } catch (Exception e) { ok("payout/banks/query", false, e.getClass().getSimpleName() + " " + e.getMessage()); }

        // 6. payout/create
        String outPayoutNo = "sdkw-" + tag;
        try {
            ApiResponse r = client.payoutCreate(map(
                    "out_payout_no", outPayoutNo, "amount", 10000, "currency", "PHP",
                    "pay_method", bankCode != null ? "bank" : "gcash", "country", "PH",
                    "notify_url", "https://merchant.example.com/api/notify/payout",
                    "account_no", "1234567890", "account_name", "San Zhang", "bank_code", bankCode));
            ok("payout/create", r.dataAsMap().get("payout_no") != null,
                    "payout_no=" + r.dataAsMap().get("payout_no") + " status=" + r.dataAsMap().get("status") + " freeze=" + r.dataAsMap().get("freeze_amount"));
        } catch (ApiException e) { ok("payout/create", false, "ApiException code=" + e.code() + " " + e.getMessage() + " " + e.data()); }
        catch (Exception e) { ok("payout/create", false, e.getClass().getSimpleName() + " " + e.getMessage()); }

        // 7. payout/query
        try {
            ApiResponse r = client.payoutQuery(map("out_payout_no", outPayoutNo));
            ok("payout/query", outPayoutNo.equals(r.dataAsMap().get("out_payout_no")),
                    "status=" + r.dataAsMap().get("status") + " sub_state=" + r.dataAsMap().get("sub_state"));
        } catch (Exception e) { ok("payout/query", false, e.getClass().getSimpleName() + " " + e.getMessage()); }

        // 8. 负例：错误密钥签名应被服务端拒（code 100104）
        try {
            Client bad = new Client(Config.builder().merchantNo(mno).apiKey(key)
                    .apiSecretPay("deadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef").apiSecretPayout(pout).baseUrl(base).build());
            bad.payQuery(map("out_order_no", outOrderNo));
            ok("负例:错误签名被拒", false, "未抛错（异常）");
        } catch (ApiException e) { ok("负例:错误签名被拒", e.code() == 100104 || e.code() == 100000, "code=" + e.code() + " " + e.getMessage()); }
        catch (Exception e) { ok("负例:错误签名被拒", false, e.getClass().getSimpleName() + " " + e.getMessage()); }

        // 9. 回调验签自证
        Map<String, Object> cb = map("merchant_no", mno, "order_no", orderNo != null ? orderNo : "P_demo",
                "out_order_no", outOrderNo, "amount", 10000, "currency", "PHP", "status", "success", "paid_at", "2026-06-23T08:00:00+08:00");
        cb.put("sign", Signer.sign(cb, pay));
        ok("回调验签 正例", client.verifyPayCallback(cb), "");
        Map<String, Object> tampered = new HashMap<>(cb);
        tampered.put("amount", 10001);
        ok("回调验签 反例(篡改amount)", !client.verifyPayCallback(tampered), "");

        System.out.println("\n[Java] 结果: " + pass + " 通过, " + fail + " 失败");
        System.exit(fail > 0 ? 1 : 0);
    }

    static String truncate(Object url) {
        String s = url == null ? "" : url.toString();
        return s.length() > 48 ? s.substring(0, 48) : s;
    }
}
