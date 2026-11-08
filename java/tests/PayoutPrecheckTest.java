import cloud.cniia.openapi.sdk.PayoutPrecheck;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 代付反查判定。任一失败以非 0 退出。
 */
public class PayoutPrecheckTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        Map<String, Object> saved = saved("abc123");
        Map<String, Object> matched = body("abc123");
        assertEquals("字段一致", PayoutPrecheck.APPROVE, PayoutPrecheck.decide(matched, saved));
        matched.put("payout_no", "W-other");
        assertEquals("payout_no 不比较", PayoutPrecheck.APPROVE, PayoutPrecheck.decide(matched, saved));

        Map<String, Object> utfSaved = saved("商户签");
        assertEquals("UTF-8 一致", PayoutPrecheck.APPROVE, PayoutPrecheck.decide(body("商户签"), utfSaved));
        assertEquals("UTF-8 不一致", PayoutPrecheck.REJECT, PayoutPrecheck.decide(body("商户钱"), utfSaved));

        assertEquals("同长度错误签名", PayoutPrecheck.REJECT, PayoutPrecheck.decide(body("abc124"), saved));
        assertEquals("不同长度", PayoutPrecheck.REJECT, PayoutPrecheck.decide(body("abc"), saved));
        Map<String, Object> otherAccount = body("abc123");
        otherAccount.put("account_no", "other");
        assertEquals("账号不一致", PayoutPrecheck.REJECT, PayoutPrecheck.decide(otherAccount, saved));
        Map<String, Object> extra = body("abc123");
        extra.put("extra", "x");
        assertEquals("多字段", PayoutPrecheck.REJECT, PayoutPrecheck.decide(extra, saved));
        Map<String, Object> missing = body("abc123");
        missing.remove("payout_no");
        assertEquals("缺字段", PayoutPrecheck.REJECT, PayoutPrecheck.decide(missing, saved));
        Map<String, Object> empty = body("abc123");
        empty.put("account_no", "");
        assertEquals("空串", PayoutPrecheck.REJECT, PayoutPrecheck.decide(empty, saved));
        Map<String, Object> number = body("abc123");
        number.put("payout_no", 1);
        assertEquals("非字符串", PayoutPrecheck.REJECT, PayoutPrecheck.decide(number, saved));
        assertEquals("保存的 sign 为空", PayoutPrecheck.REJECT, PayoutPrecheck.decide(body("abc123"), saved("")));
        assertEquals("body 为 null", PayoutPrecheck.REJECT, PayoutPrecheck.decide(null, saved));
        assertEquals("saved 为 null", PayoutPrecheck.REJECT, PayoutPrecheck.decide(body("abc123"), null));

        System.out.println("通过: " + passed + "  失败: " + failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static Map<String, Object> saved(String sign) {
        Map<String, Object> saved = new LinkedHashMap<>();
        saved.put("out_payout_no", "merchant-order-no");
        saved.put("account_no", "account");
        saved.put("sign", sign);
        return saved;
    }

    private static Map<String, Object> body(String originalSign) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("out_payout_no", "merchant-order-no");
        body.put("payout_no", "W100");
        body.put("account_no", "account");
        body.put("original_sign", originalSign);
        return body;
    }

    private static void assertEquals(String label, Object expected, Object actual) {
        if (expected == null ? actual == null : expected.equals(actual)) {
            passed++;
            System.out.println("  PASS " + label);
        } else {
            failed++;
            System.out.println("  FAIL " + label + " expected=" + expected + " actual=" + actual);
        }
    }
}
