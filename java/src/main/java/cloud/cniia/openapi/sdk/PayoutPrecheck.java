package cloud.cniia.openapi.sdk;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.Set;

/**
 * 商户代付反查判定。平台 POST 到商户自己的 HTTPS 接口，本类不发请求。
 *
 * <p>请求体必须恰好是四个非空字符串：account_no、out_payout_no、original_sign、payout_no。
 * original_sign 与下单时保存的 sign 按 UTF-8 字节等长比较。payout_no 必须出现，但不与下单请求比较。
 * 返回值只有精确的 approve 或 reject。非法输入返回 reject，不抛异常。
 */
public final class PayoutPrecheck {

    public static final String APPROVE = "approve";
    public static final String REJECT = "reject";

    private static final Set<String> BODY_KEYS = Set.of(
            "account_no", "out_payout_no", "original_sign", "payout_no");

    private PayoutPrecheck() { }

    /**
     * @param body  平台 POST 的 JSON 对象
     * @param saved 下单时保存的 out_payout_no、account_no、sign
     * @return 精确的 {@code approve} 或 {@code reject}
     */
    public static String decide(Object body, Object saved) {
        try {
            if (!(body instanceof Map) || !(saved instanceof Map)) {
                return REJECT;
            }
            Map<?, ?> bodyMap = (Map<?, ?>) body;
            Map<?, ?> savedMap = (Map<?, ?>) saved;
            if (!bodyMap.keySet().equals(BODY_KEYS) || !allNonEmptyStrings(bodyMap)) {
                return REJECT;
            }
            String savedAccount = nonEmptyString(savedMap.get("account_no"));
            String savedOut = nonEmptyString(savedMap.get("out_payout_no"));
            String savedSign = nonEmptyString(savedMap.get("sign"));
            if (savedAccount == null || savedOut == null || savedSign == null) {
                return REJECT;
            }
            if (!savedAccount.equals(bodyMap.get("account_no"))
                    || !savedOut.equals(bodyMap.get("out_payout_no"))) {
                return REJECT;
            }
            byte[] actual = ((String) bodyMap.get("original_sign")).getBytes(StandardCharsets.UTF_8);
            byte[] expected = savedSign.getBytes(StandardCharsets.UTF_8);
            if (actual.length == expected.length && MessageDigest.isEqual(actual, expected)) {
                return APPROVE;
            }
            return REJECT;
        } catch (RuntimeException ex) {
            return REJECT;
        }
    }

    private static boolean allNonEmptyStrings(Map<?, ?> values) {
        for (Object value : values.values()) {
            if (nonEmptyString(value) == null) {
                return false;
            }
        }
        return true;
    }

    private static String nonEmptyString(Object value) {
        if (!(value instanceof String)) {
            return null;
        }
        String text = (String) value;
        if (text.isEmpty()) {
            return null;
        }
        return text;
    }
}
