import cloud.cniia.openapi.sdk.Json;
import cloud.cniia.openapi.sdk.Signer;
import cloud.cniia.openapi.sdk.Config;
import cloud.cniia.openapi.sdk.Environment;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 修复项的最小自检（仅覆盖新增 fail-closed 守卫，不替代 VectorTest）。
 * 任一断言失败以非 0 退出。
 */
public class GuardCheck {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        // ---------- A：空/空白密钥 fail-closed ----------
        Map<String, Object> cb = new LinkedHashMap<>();
        cb.put("amount", 100L);
        cb.put("status", "success");
        // 用真密钥先算一个合法 sign
        String realSecret = "sk_test_0123456789abcdef0123456789abcdef";
        String okSign = Signer.sign(cb, realSecret);
        cb.put("sign", okSign);

        assertTrue("verifyCallback 空串密钥 → false", !Signer.verifyCallback(cb, ""));
        assertTrue("verifyCallback 全空白密钥 → false", !Signer.verifyCallback(cb, "   "));
        assertTrue("verifyCallback null 密钥 → false", !Signer.verifyCallback(cb, null));
        assertTrue("verifyCallback 合法密钥 → true", Signer.verifyCallback(cb, realSecret));

        assertThrows("sign 空密钥拒绝", () -> Signer.sign(cb, ""));
        assertThrows("sign 全空白密钥拒绝", () -> Signer.sign(cb, "  "));
        assertThrows("sign null 密钥拒绝", () -> Signer.sign(cb, null));
        assertThrows("buildSignBase 空密钥拒绝", () -> Signer.buildSignBase(cb, ""));

        // ---------- B：验签异常归 false ----------
        assertTrue("verifyCallback payload=null → false", !Signer.verifyCallback(null, realSecret));
        Map<String, Object> noSign = new LinkedHashMap<>();
        noSign.put("amount", 1L);
        assertTrue("verifyCallback 缺 sign → false", !Signer.verifyCallback(noSign, realSecret));
        Map<String, Object> nonStrSign = new LinkedHashMap<>();
        nonStrSign.put("amount", 1L);
        nonStrSign.put("sign", 12345L); // 非字符串 sign
        assertTrue("verifyCallback 非字符串 sign → false", !Signer.verifyCallback(nonStrSign, realSecret));
        Map<String, Object> badCharSign = new LinkedHashMap<>();
        badCharSign.put("amount", 1L);
        badCharSign.put("sign", "zzzz!!!!"); // 非 hex 字符
        assertTrue("verifyCallback 非 hex sign → false", !Signer.verifyCallback(badCharSign, realSecret));
        Map<String, Object> emptySign = new LinkedHashMap<>();
        emptySign.put("amount", 1L);
        emptySign.put("sign", ""); // 空 sign
        assertTrue("verifyCallback 空 sign → false", !Signer.verifyCallback(emptySign, realSecret));
        // 含非法数值字段（NaN）的回调，验签内部 sign 计算会抛错 → 应判 false 而非冒泡
        Map<String, Object> nanCb = new LinkedHashMap<>();
        nanCb.put("amount", Double.NaN);
        nanCb.put("sign", "abc");
        assertTrue("verifyCallback 含 NaN 字段 → false (不冒泡)", !Signer.verifyCallback(nanCb, realSecret));

        // ---------- C：数值规范（通过公开 buildSignBase 间接走 valueForSign/doubleForSign） ----------
        assertThrows("NaN 字段拒绝", () -> Signer.buildSignBase(num("v", Double.NaN), "sk"));
        assertThrows("+Infinity 字段拒绝", () -> Signer.buildSignBase(num("v", Double.POSITIVE_INFINITY), "sk"));
        assertThrows("-Infinity 字段拒绝", () -> Signer.buildSignBase(num("v", Double.NEGATIVE_INFINITY), "sk"));
        assertThrows("非整数浮点拒绝", () -> Signer.buildSignBase(num("v", 1.5), "sk"));
        // 远超 2^53，旧代码会饱和成 Long.MAX_VALUE 静默改写
        assertThrows("超 2^53 拒绝", () -> Signer.buildSignBase(num("v", 1.0e19), "sk"));
        assertEquals("-0.0 → \"0\"", "v=0&secret=sk", Signer.buildSignBase(num("v", -0.0), "sk"));
        assertEquals("0.0 → \"0\"", "v=0&secret=sk", Signer.buildSignBase(num("v", 0.0), "sk"));
        assertEquals("整数浮点 → 文本", "v=10000&secret=sk", Signer.buildSignBase(num("v", 10000.0), "sk"));
        // 恰好 2^53 放行
        assertEquals("2^53 放行", "v=9007199254740992&secret=sk",
                Signer.buildSignBase(num("v", 9007199254740992.0), "sk"));

        // Json.stableStringify 同样应用数值守卫
        Map<String, Object> jm = new LinkedHashMap<>();
        jm.put("v", Double.NaN);
        assertThrows("stableStringify NaN 拒绝", () -> Json.stableStringify(jm));
        Map<String, Object> jm2 = new LinkedHashMap<>();
        jm2.put("v", 2.5);
        assertThrows("stableStringify 非整数拒绝", () -> Json.stableStringify(jm2));

        // ---------- [LOW] Json.parseNumber fail-closed ----------
        assertThrowsJson("parse 畸形数字 '-' → JsonException", () -> Json.parse("-"));
        assertThrowsJson("parse 畸形数字 '1.2.3' → JsonException", () -> Json.parse("1.2.3"));
        assertThrowsJson("parse 畸形数字 '1e' → JsonException", () -> Json.parse("1e"));
        // 合法数字仍正常
        assertEquals("parse 合法整数", 10000L, Json.parse("10000"));

        // ---------- D：Config https 强制 ----------
        // 非 localhost 的 http 生产地址应被拒绝
        assertThrows("Config http 生产地址拒绝", () -> Config.builder()
                .baseUrl("http://api.agent.example.com/api/open/v1")
                .merchantNo("M1").apiKey("k").apiSecretPay("p").build());
        // https 生产地址放行
        Config httpsCfg = Config.builder()
                .baseUrl("https://api.agent.example.com/api/open/v1")
                .merchantNo("M1").apiKey("k").apiSecretPay("p").build();
        assertEquals("Config https 生产地址放行",
                "https://api.agent.example.com/api/open/v1", httpsCfg.baseUrl());
        // localhost http 放行
        Config localCfg = Config.builder()
                .baseUrl("http://localhost:3090/api/open/v1")
                .merchantNo("M1").apiKey("k").apiSecretPay("p").build();
        assertEquals("Config localhost http 放行",
                "http://localhost:3090/api/open/v1", localCfg.baseUrl());
        // 127.0.0.1 http 放行
        Config loopback = Config.builder()
                .baseUrl("http://127.0.0.1:3090/api/open/v1")
                .merchantNo("M1").apiKey("k").apiSecretPay("p").build();
        assertEquals("Config 127.0.0.1 http 放行",
                "http://127.0.0.1:3090/api/open/v1", loopback.baseUrl());
        // SANDBOX 环境内置 http 地址放行
        Config sandboxCfg = Config.builder()
                .environment(Environment.SANDBOX)
                .merchantNo("M1").apiKey("k").apiSecretPay("p").build();
        assertEquals("Config SANDBOX http 放行",
                "http://127.0.0.1:3090/api/open/v1", sandboxCfg.baseUrl());
        // 一个伪装成 localhost 子串但实际是别的主机的地址应被拒绝
        assertThrows("Config localhost.evil.com http 拒绝", () -> Config.builder()
                .baseUrl("http://localhost.evil.com/api/open/v1")
                .merchantNo("M1").apiKey("k").apiSecretPay("p").build());

        System.out.println();
        System.out.println("================ GuardCheck 结果 ================");
        System.out.println("通过: " + passed + "  失败: " + failed);
        if (failed > 0) {
            System.out.println("FAIL");
            System.exit(1);
        }
        System.out.println("ALL PASS");
    }

    /** 构造单字段 payload 工具。 */
    private static Map<String, Object> num(String k, Object v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(k, v);
        return m;
    }

    interface Block { void run(); }

    private static void assertThrows(String label, Block b) {
        try {
            b.run();
            failed++;
            System.out.println("  FAIL " + label + " (未抛异常)");
        } catch (IllegalArgumentException | IllegalStateException e) {
            passed++;
            System.out.println("  PASS " + label + " (" + e.getClass().getSimpleName() + ")");
        } catch (RuntimeException e) {
            failed++;
            System.out.println("  FAIL " + label + " (抛了非预期异常 " + e.getClass().getName() + ")");
        }
    }

    private static void assertThrowsJson(String label, Block b) {
        try {
            b.run();
            failed++;
            System.out.println("  FAIL " + label + " (未抛异常)");
        } catch (Json.JsonException e) {
            passed++;
            System.out.println("  PASS " + label);
        } catch (RuntimeException e) {
            failed++;
            System.out.println("  FAIL " + label + " (抛了非 JsonException: " + e.getClass().getName() + ")");
        }
    }

    private static void assertTrue(String label, boolean cond) {
        if (cond) {
            passed++;
            System.out.println("  PASS " + label);
        } else {
            failed++;
            System.out.println("  FAIL " + label);
        }
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
