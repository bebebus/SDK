package cloud.cniia.projectp.sdk;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.math.BigInteger;
import java.math.BigDecimal;

/**
 * 零依赖 JSON 实现。
 *
 * <p>本类承担三件事：
 * <ul>
 *   <li>{@link #parse(String)} — 把 JSON 文本解析成 Java 值（Map/List/String/Long/BigInteger/BigDecimal/Boolean/null），
 *       用于读取 test-vectors.json 与解析回调/响应体。</li>
 *   <li>{@link #stableStringify(Object)} — 稳定序列化（key 递归升序、紧凑无空格、JS JSON.stringify 对齐的转义），
 *       供签名器对 object/array 取值字符串使用。</li>
 *   <li>{@link #serialize(Object)} — 序列化请求体为 JSON 文本（不强制 key 升序，但本 SDK 请求体始终用排序后的有序 Map，效果等价）。</li>
 * </ul>
 *
 * <p>关键约束（见 SIGNING.md §三）：
 * <ul>
 *   <li>整数 token 解析成 {@link Long}（超出 long 范围则 {@link BigInteger}），绝不退化为 double，
 *       保证 {@code String} 化为 "10000" 而非 "10000.0" 或 "1e+12"。</li>
 *   <li>字符串转义只转双引号、反斜杠、b/f/n/r/t 及其余 U+0000-U+001F 控制字符（小写四位 hex）；
 *       不转正斜杠、非 ASCII、尖括号与 &amp;。</li>
 * </ul>
 */
public final class Json {

    private Json() {}

    // ===================== 序列化（签名用稳定形态） =====================

    /**
     * 稳定 JSON 序列化：object key 递归升序、紧凑无空格、转义对齐 JS JSON.stringify。
     * 用于把 payload 中的 object/array 字段转成签名取值字符串。
     */
    public static String stableStringify(Object value) {
        StringBuilder sb = new StringBuilder();
        writeStable(sb, value);
        return sb.toString();
    }

    /**
     * 普通序列化：用于发送请求体。请求体本 SDK 已用有序 Map 构造，故此处与 stableStringify 行为一致。
     */
    public static String serialize(Object value) {
        return stableStringify(value);
    }

    private static void writeStable(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String) {
            writeJsonString(sb, (String) value);
        } else if (value instanceof Boolean) {
            sb.append(((Boolean) value) ? "true" : "false");
        } else if (value instanceof Map) {
            writeObject(sb, (Map<?, ?>) value);
        } else if (value instanceof Iterable) {
            writeArray(sb, (Iterable<?>) value);
        } else if (value instanceof Object[]) {
            writeArray(sb, java.util.Arrays.asList((Object[]) value));
        } else if (value instanceof Long || value instanceof Integer
                || value instanceof Short || value instanceof Byte
                || value instanceof BigInteger) {
            // 整数：原始十进制文本，无小数点
            sb.append(value.toString());
        } else if (value instanceof BigDecimal) {
            sb.append(((BigDecimal) value).toPlainString());
        } else if (value instanceof Double || value instanceof Float) {
            // 数字字面量（API 不用浮点金额，但为完整性保留）。整数值的 double 去掉 .0。
            double d = ((Number) value).doubleValue();
            if (d == Math.rint(d) && !Double.isInfinite(d) && !Double.isNaN(d)) {
                sb.append(Long.toString((long) d));
            } else {
                sb.append(Double.toString(d));
            }
        } else {
            // 兜底：当字符串处理
            writeJsonString(sb, value.toString());
        }
    }

    private static void writeObject(StringBuilder sb, Map<?, ?> map) {
        // key 升序（按 String 自然序，等价 String.compareTo / 码点升序）
        TreeMap<String, Object> sorted = new TreeMap<>();
        for (Map.Entry<?, ?> e : map.entrySet()) {
            sorted.put(String.valueOf(e.getKey()), e.getValue());
        }
        sb.append('{');
        boolean first = true;
        for (Map.Entry<String, Object> e : sorted.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            writeJsonString(sb, e.getKey());
            sb.append(':');
            writeStable(sb, e.getValue());
        }
        sb.append('}');
    }

    private static void writeArray(StringBuilder sb, Iterable<?> arr) {
        sb.append('[');
        boolean first = true;
        for (Object item : arr) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            writeStable(sb, item);
        }
        sb.append(']');
    }

    /**
     * JSON 字符串转义（带双引号）。对齐 JS JSON.stringify：
     * 只转 " \ \b\f\n\r\t 及其余 U+0000–U+001F；不转 /、非 ASCII、<>&。
     */
    static void writeJsonString(StringBuilder sb, String s) {
        sb.append('"');
        int len = s.length();
        for (int i = 0; i < len; i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append("\\u");
                        String hex = Integer.toHexString(c); // 小写
                        for (int p = hex.length(); p < 4; p++) {
                            sb.append('0');
                        }
                        sb.append(hex);
                    } else {
                        // 非 ASCII 与 / <>& 原样输出
                        sb.append(c);
                    }
                    break;
            }
        }
        sb.append('"');
    }

    // ===================== 解析 =====================

    /**
     * 解析 JSON 文本为 Java 值。
     * 类型映射：object→LinkedHashMap, array→ArrayList, string→String,
     * 整数→Long(溢出时 BigInteger), 小数→BigDecimal, true/false→Boolean, null→null。
     */
    public static Object parse(String text) {
        Parser p = new Parser(text);
        p.skipWs();
        Object v = p.parseValue();
        p.skipWs();
        if (!p.atEnd()) {
            throw new JsonException("JSON 末尾存在多余字符，位置 " + p.pos);
        }
        return v;
    }

    /** 解析为 Map（顶层必须是 object）。 */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String text) {
        Object v = parse(text);
        if (!(v instanceof Map)) {
            throw new JsonException("期望 JSON object，实际是 " + (v == null ? "null" : v.getClass().getSimpleName()));
        }
        return (Map<String, Object>) v;
    }

    /** JSON 解析/序列化异常。 */
    public static final class JsonException extends RuntimeException {
        public JsonException(String message) {
            super(message);
        }
    }

    private static final class Parser {
        private final String s;
        private int pos;

        Parser(String s) {
            this.s = s;
        }

        boolean atEnd() {
            return pos >= s.length();
        }

        void skipWs() {
            while (pos < s.length()) {
                char c = s.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    pos++;
                } else {
                    break;
                }
            }
        }

        Object parseValue() {
            skipWs();
            if (atEnd()) {
                throw new JsonException("意外的 JSON 结束");
            }
            char c = s.charAt(pos);
            switch (c) {
                case '{':
                    return parseObjectInternal();
                case '[':
                    return parseArrayInternal();
                case '"':
                    return parseString();
                case 't':
                case 'f':
                    return parseBoolean();
                case 'n':
                    return parseNull();
                default:
                    if (c == '-' || (c >= '0' && c <= '9')) {
                        return parseNumber();
                    }
                    throw new JsonException("意外字符 '" + c + "'，位置 " + pos);
            }
        }

        private Map<String, Object> parseObjectInternal() {
            Map<String, Object> map = new LinkedHashMap<>();
            expect('{');
            skipWs();
            if (peek() == '}') {
                pos++;
                return map;
            }
            while (true) {
                skipWs();
                if (peek() != '"') {
                    throw new JsonException("对象 key 必须是字符串，位置 " + pos);
                }
                String key = parseString();
                skipWs();
                expect(':');
                Object value = parseValue();
                map.put(key, value);
                skipWs();
                char n = next();
                if (n == '}') {
                    break;
                }
                if (n != ',') {
                    throw new JsonException("对象中期望 ',' 或 '}'，位置 " + (pos - 1));
                }
            }
            return map;
        }

        private List<Object> parseArrayInternal() {
            List<Object> list = new ArrayList<>();
            expect('[');
            skipWs();
            if (peek() == ']') {
                pos++;
                return list;
            }
            while (true) {
                Object value = parseValue();
                list.add(value);
                skipWs();
                char n = next();
                if (n == ']') {
                    break;
                }
                if (n != ',') {
                    throw new JsonException("数组中期望 ',' 或 ']'，位置 " + (pos - 1));
                }
            }
            return list;
        }

        private String parseString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (atEnd()) {
                    throw new JsonException("字符串未闭合");
                }
                char c = s.charAt(pos++);
                if (c == '"') {
                    break;
                }
                if (c == '\\') {
                    if (atEnd()) {
                        throw new JsonException("转义未闭合");
                    }
                    char e = s.charAt(pos++);
                    switch (e) {
                        case '"': sb.append('"'); break;
                        case '\\': sb.append('\\'); break;
                        case '/': sb.append('/'); break;
                        case 'b': sb.append('\b'); break;
                        case 'f': sb.append('\f'); break;
                        case 'n': sb.append('\n'); break;
                        case 'r': sb.append('\r'); break;
                        case 't': sb.append('\t'); break;
                        case 'u':
                            if (pos + 4 > s.length()) {
                                throw new JsonException("\\u 转义不完整");
                            }
                            String hex = s.substring(pos, pos + 4);
                            pos += 4;
                            sb.append((char) Integer.parseInt(hex, 16));
                            break;
                        default:
                            throw new JsonException("非法转义 \\" + e);
                    }
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }

        private Object parseNumber() {
            int start = pos;
            boolean isFloat = false;
            if (peek() == '-') {
                pos++;
            }
            while (!atEnd()) {
                char c = s.charAt(pos);
                if (c >= '0' && c <= '9') {
                    pos++;
                } else if (c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
                    isFloat = true;
                    pos++;
                } else {
                    break;
                }
            }
            String num = s.substring(start, pos);
            if (isFloat) {
                return new BigDecimal(num);
            }
            try {
                return Long.parseLong(num);
            } catch (NumberFormatException ex) {
                return new BigInteger(num);
            }
        }

        private Boolean parseBoolean() {
            if (s.startsWith("true", pos)) {
                pos += 4;
                return Boolean.TRUE;
            }
            if (s.startsWith("false", pos)) {
                pos += 5;
                return Boolean.FALSE;
            }
            throw new JsonException("非法布尔字面量，位置 " + pos);
        }

        private Object parseNull() {
            if (s.startsWith("null", pos)) {
                pos += 4;
                return null;
            }
            throw new JsonException("非法 null 字面量，位置 " + pos);
        }

        private char peek() {
            if (atEnd()) {
                throw new JsonException("意外的 JSON 结束");
            }
            return s.charAt(pos);
        }

        private char next() {
            if (atEnd()) {
                throw new JsonException("意外的 JSON 结束");
            }
            return s.charAt(pos++);
        }

        private void expect(char c) {
            char n = next();
            if (n != c) {
                throw new JsonException("期望 '" + c + "' 实际 '" + n + "'，位置 " + (pos - 1));
            }
        }
    }
}
