package cloud.cniia.projectp.sdk;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * HMAC-SHA256 签名器（输出十六进制小写）。
 *
 * <p>算法严格对齐 {@code SIGNING.md}：
 * <ol>
 *   <li>过滤：丢弃 key=="sign" 与值为 null 的字段。</li>
 *   <li>排序：剩余 key 按 ASCII/码点升序。</li>
 *   <li>取值：标量用原始字符串形态（不加引号），object/array 用 {@link Json#stableStringify}。</li>
 *   <li>拼接：{@code k1=v1&...&kN=vN&secret=<secret>}。</li>
 *   <li>HMAC：{@code HMAC_SHA256(base, key=secret)} → hex 小写。</li>
 * </ol>
 */
public final class Signer {

    private Signer() {}

    /**
     * 构造签名 base 串（不计算 HMAC）。便于测试逐字节断言。
     *
     * @param payload 业务字段表（不含 sign；含 sign 也会被自动过滤）
     * @param secret  与请求/回调匹配的密钥
     */
    public static String buildSignBase(Map<String, Object> payload, String secret) {
        // TreeMap 保证 key 升序（String 自然序 = compareTo = 码点升序）
        TreeMap<String, Object> sorted = new TreeMap<>();
        for (Map.Entry<String, Object> e : payload.entrySet()) {
            String key = e.getKey();
            if ("sign".equals(key)) {
                continue; // 过滤 sign
            }
            Object value = e.getValue();
            if (value == null) {
                continue; // 过滤 null
            }
            sorted.put(key, value);
        }

        List<String> parts = new ArrayList<>(sorted.size());
        for (Map.Entry<String, Object> e : sorted.entrySet()) {
            parts.add(e.getKey() + "=" + valueForSign(e.getValue()));
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                sb.append('&');
            }
            sb.append(parts.get(i));
        }
        if (sb.length() > 0) {
            sb.append('&');
        }
        sb.append("secret=").append(secret);
        return sb.toString();
    }

    /**
     * 计算签名（hex 小写）。
     *
     * @param payload 业务字段表（不含 sign）
     * @param secret  与请求/回调匹配的密钥
     */
    public static String sign(Map<String, Object> payload, String secret) {
        String base = buildSignBase(payload, secret);
        return hmacSha256Hex(base, secret);
    }

    /**
     * 回调验签：按"除 sign 外所有字段参与"通用计算，与回调里的 sign 时序安全比较。
     *
     * @param payload 解析后的回调字段表（含 sign）
     * @param secret  代收回调用 api_secret_pay；代付回调用 api_secret_payout
     * @return 验签是否通过
     */
    public static boolean verifyCallback(Map<String, Object> payload, String secret) {
        Object provided = payload.get("sign");
        if (!(provided instanceof String)) {
            return false;
        }
        String expected = sign(payload, secret);
        // 时序安全比较（按 UTF-8 字节比较，长度不同也安全返回 false）
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                ((String) provided).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 取值字符串：标量原始字符串（不加引号），object/array 走稳定序列化。
     */
    static String valueForSign(Object v) {
        if (v == null) {
            // 调用方已过滤；防御性返回空
            return "";
        }
        if (v instanceof String) {
            return (String) v; // 顶层 string 原样、不转义、不加引号
        }
        if (v instanceof Boolean) {
            return ((Boolean) v) ? "true" : "false";
        }
        if (v instanceof Long || v instanceof Integer || v instanceof Short
                || v instanceof Byte || v instanceof BigInteger) {
            return v.toString(); // 整数十进制文本
        }
        if (v instanceof BigDecimal) {
            return ((BigDecimal) v).toPlainString();
        }
        if (v instanceof Double || v instanceof Float) {
            double d = ((Number) v).doubleValue();
            if (d == Math.rint(d) && !Double.isInfinite(d) && !Double.isNaN(d)) {
                return Long.toString((long) d);
            }
            return Double.toString(d);
        }
        if (v instanceof Map || v instanceof Iterable || v instanceof Object[]) {
            return Json.stableStringify(v); // 嵌套对象/数组
        }
        // 兜底
        return v.toString();
    }

    /** HMAC-SHA256(message, key) → hex 小写。 */
    static String hmacSha256Hex(String message, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            return toHexLower(digest);
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 计算失败", e);
        }
    }

    private static String toHexLower(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        final char[] HEX = "0123456789abcdef".toCharArray();
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            hexChars[i * 2] = HEX[v >>> 4];
            hexChars[i * 2 + 1] = HEX[v & 0x0F];
        }
        return new String(hexChars);
    }
}
