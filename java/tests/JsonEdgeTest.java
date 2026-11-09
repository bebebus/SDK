// SPDX-License-Identifier: MIT

import cloud.cniia.openapi.sdk.Json;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;

/** 验证不可信响应的 JSON 边界与稳定序列化。 */
public final class JsonEdgeTest {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        check(Json.parse("  null \n") == null, "null 与空白");
        check(Boolean.TRUE.equals(Json.parse("true")), "true");
        check(Boolean.FALSE.equals(Json.parse("false")), "false");
        check(new BigInteger("9223372036854775808").equals(Json.parse("9223372036854775808")), "大整数");
        check(new BigDecimal("1.25e+2").compareTo((BigDecimal) Json.parse("1.25e+2")) == 0, "指数小数");
        check("\"\\/\b\f\n\r\tA".equals(Json.parse("\"\\\"\\\\\\/\\b\\f\\n\\r\\t\\u0041\"")), "字符串转义");
        check(List.of().equals(Json.parse("[]")), "空数组");
        check(Map.of().equals(Json.parse("{}")), "空对象");
        check("[1,true,null,\"x\"]".equals(Json.serialize(new Object[] {1, true, null, "x"})), "数组序列化");
        check("\"\\b\\f\\n\\r\\t\\u0001\"".equals(Json.serialize("\b\f\n\r\t\u0001")), "控制字符转义");
        check("1.20".equals(Json.serialize(new BigDecimal("1.20"))), "BigDecimal 序列化");
        check("\"fallback\"".equals(Json.serialize(new Object() {
            @Override public String toString() { return "fallback"; }
        })), "其他对象序列化");
        for (String invalid : new String[] {"", "[", "{", "{1:2}", "[1 2]", "{\"a\":1 2}",
                "\"unfinished", "\"\\x\"", "\"\\u12\"", "-", "1e", "truth", "nul", "true x"}) {
            try {
                Json.parse(invalid);
                throw new AssertionError("应拒绝非法 JSON: " + invalid);
            } catch (Json.JsonException expected) {
                // 非法输入必须被明确拒绝。
            }
        }
        try {
            Json.parseObject("[]");
            throw new AssertionError("顶层非对象应被拒绝");
        } catch (Json.JsonException expected) {
            // 需要对象时不接受数组。
        }
        System.out.println("JsonEdgeTest 通过");
    }
}
