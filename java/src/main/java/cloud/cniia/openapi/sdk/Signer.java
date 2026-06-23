package cloud.cniia.openapi.sdk;

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
        // 空/空白密钥一律拒绝：从根上禁止用空密钥签名（fail-closed）。
        if (!isUsableSecret(secret)) {
            throw new IllegalArgumentException("secret 不能为空或全空白");
        }
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
        // 空/空白密钥拒绝（与 buildSignBase 一致，从根上禁止空密钥签名）。
        if (!isUsableSecret(secret)) {
            throw new IllegalArgumentException("secret 不能为空或全空白");
        }
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
        // 空/空白密钥：在计算任何 HMAC 之前直接拒绝（fail-closed），不进入签名比较。
        if (!isUsableSecret(secret)) {
            return false;
        }
        // 回调体非法（null）一律拒绝，绝不抛异常冒泡。
        if (payload == null) {
            return false;
        }
        // 任何攻击者可控的异常输入都收敛为 false，不抛异常。
        try {
            Object provided = payload.get("sign");
            if (!(provided instanceof String)) {
                return false;
            }
            String providedSign = (String) provided;
            // 提供的 sign 形态异常（空/超长/非 hex 字符）直接判败，避免无谓计算。
            if (!isPlausibleSign(providedSign)) {
                return false;
            }
            String expected = sign(payload, secret);
            // 时序安全比较（按 UTF-8 字节比较，长度不同也安全返回 false）
            return MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8),
                    providedSign.getBytes(StandardCharsets.UTF_8));
        } catch (RuntimeException e) {
            // 非法数值、非法嵌套等导致 sign 计算抛错时，验签判败而非冒泡。
            return false;
        }
    }

    /** 密钥可用性：非 null 且去除空白后非空。空/全空白一律不可用（fail-closed）。 */
    static boolean isUsableSecret(String secret) {
        return secret != null && !secret.trim().isEmpty();
    }

    /**
     * 粗判提供的 sign 是否像一个 HMAC-SHA256 hex（小写 64 位）。
     * 仅用于尽早拒绝明显畸形/非字符串的 sign；不放宽合法签名（时序安全比较仍是最终裁决）。
     * 这里只做长度与字符集的保守校验：长度异常或含非 hex 字符即判非法。
     */
    private static boolean isPlausibleSign(String sign) {
        int len = sign.length();
        // 标准 HMAC-SHA256 hex 为 64 字符；放宽到 [1,128] 仅防超长/空串攻击，最终比较仍按字节。
        if (len == 0 || len > 128) {
            return false;
        }
        for (int i = 0; i < len; i++) {
            char c = sign.charAt(i);
            boolean isHex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
            if (!isHex) {
                return false;
            }
        }
        return true;
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
            return doubleForSign(((Number) v).doubleValue());
        }
        if (v instanceof Map || v instanceof Iterable || v instanceof Object[]) {
            return Json.stableStringify(v); // 嵌套对象/数组
        }
        // 兜底
        return v.toString();
    }

    /**
     * 浮点数参与签名的规范化（fail-fast，合约本就要求整数最小单位）。
     * <ul>
     *   <li>NaN / Infinity 一律拒绝；</li>
     *   <li>非整数（有小数部分）拒绝——金额应以整数最小单位传入；</li>
     *   <li>整数但 |d| 超过 2^53（double 可精确表示整数上界）拒绝，避免强转 long 时被
     *       静默饱和/截断为另一个错值；</li>
     *   <li>-0.0 归一为 "0"。</li>
     * </ul>
     * 与 JS 的 {@code Number → String} 在“整数最小单位”这一合约范围内严格对齐。
     */
    static String doubleForSign(double d) {
        if (Double.isNaN(d) || Double.isInfinite(d)) {
            throw new IllegalArgumentException("数值字段不能为 NaN/Infinity");
        }
        if (d != Math.rint(d)) {
            throw new IllegalArgumentException("数值字段必须是整数（请用整数最小单位），收到非整数: " + d);
        }
        // 2^53：double 能精确表示连续整数的上界；超界后 long 强转会改写为错值。
        if (Math.abs(d) > 9007199254740992.0) {
            throw new IllegalArgumentException("数值字段超出可精确表示范围 (|d| > 2^53): " + d);
        }
        long l = (long) d;
        if (l == 0L) {
            return "0"; // 同时把 -0.0 归一为 "0"
        }
        return Long.toString(l);
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
