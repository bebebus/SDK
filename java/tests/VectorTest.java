import cloud.cniia.openapi.sdk.Json;
import cloud.cniia.openapi.sdk.Signer;
import cloud.cniia.openapi.sdk.Config;
import cloud.cniia.openapi.sdk.Client;
import cloud.cniia.openapi.sdk.Environment;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 独立断言 runner（无 JUnit）。
 *
 * <p>覆盖：
 * <ul>
 *   <li>(a) 读取 ../test-vectors.json，对每个向量断言 buildSignBase==base 且 sign==sign。</li>
 *   <li>(b) 回调验签正例 + 篡改一字节反例。</li>
 *   <li>额外：JSON 解析/转义边界、inline 归一、Config/Environment 基址覆盖、null 过滤。</li>
 * </ul>
 *
 * <p>断言失败累计计数；任一失败则以非 0 退出（供 run-tests.sh / CI 判定）。
 */
public class VectorTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) throws Exception {
        Path vectorsPath = resolveVectorsPath();
        System.out.println("读取签名向量: " + vectorsPath.toAbsolutePath());
        String json = new String(Files.readAllBytes(vectorsPath), StandardCharsets.UTF_8);

        runVectorTests(json);
        runCallbackTests();
        runMiscTests();

        System.out.println();
        System.out.println("================ 结果 ================");
        System.out.println("通过: " + passed + "  失败: " + failed);
        if (failed > 0) {
            System.out.println("FAIL: 存在断言失败。");
            System.exit(1);
        }
        System.out.println("ALL PASS");
    }

    // ---------------- (a) 向量：base 与 sign ----------------

    @SuppressWarnings("unchecked")
    private static void runVectorTests(String json) {
        Map<String, Object> root = Json.parseObject(json);
        List<Object> vectors = (List<Object>) root.get("vectors");
        System.out.println("\n--- 签名向量 (" + vectors.size() + ") ---");
        for (Object o : vectors) {
            Map<String, Object> v = (Map<String, Object>) o;
            String name = (String) v.get("name");
            String secret = (String) v.get("secret");
            Map<String, Object> payload = (Map<String, Object>) v.get("payload");
            String expectedBase = (String) v.get("base");
            String expectedSign = (String) v.get("sign");

            String actualBase = Signer.buildSignBase(payload, secret);
            String actualSign = Signer.sign(payload, secret);

            assertEquals("[" + name + "] base", expectedBase, actualBase);
            assertEquals("[" + name + "] sign", expectedSign, actualSign);
        }
    }

    // ---------------- (b) 回调验签正例/反例 ----------------

    private static void runCallbackTests() {
        System.out.println("\n--- 回调验签 ---");
        String paySecret = "sk_pay_secret_aaaaaaaaaaaaaaaaaaaaaaaa";
        String payoutSecret = "sk_payout_secret_bbbbbbbbbbbbbbbbbbbb";

        // 代收回调（用 pay 密钥）
        Map<String, Object> payCb = new LinkedHashMap<>();
        payCb.put("merchant_no", "M00000001");
        payCb.put("order_no", "P202501010001");
        payCb.put("out_order_no", "202501010001");
        payCb.put("amount", 10000L);
        payCb.put("actual_amount", 10000L);
        payCb.put("fee_amount", 180L);
        payCb.put("net_amount", 9820L);
        payCb.put("currency", "PHP");
        payCb.put("status", "success");
        payCb.put("channel_order_no", null); // null 不参与签名
        payCb.put("paid_at", "2025-01-01T08:00:00+08:00");
        String paySign = Signer.sign(payCb, paySecret);
        payCb.put("sign", paySign);

        assertTrue("代收回调正例验签通过", Signer.verifyCallback(payCb, paySecret));
        // 用错密钥应失败
        assertTrue("代收回调错密钥验签失败", !Signer.verifyCallback(payCb, payoutSecret));

        // 通过 Client 验签（密钥自动选 pay）
        Config payCfg = Config.builder()
                .environment(Environment.SANDBOX)
                .merchantNo("M00000001").apiKey("ak")
                .apiSecretPay(paySecret).apiSecretPayout(payoutSecret)
                .build();
        Client client = new Client(payCfg);
        assertTrue("Client.verifyPayCallback 正例", client.verifyPayCallback(payCb));

        // 篡改一字节：把 amount 改成 10001，sign 不变 → 反例
        Map<String, Object> tampered = new LinkedHashMap<>(payCb);
        tampered.put("amount", 10001L);
        assertTrue("代收回调篡改字段反例", !Signer.verifyCallback(tampered, paySecret));

        // 篡改 sign 一字节 → 反例
        Map<String, Object> tamperedSign = new LinkedHashMap<>(payCb);
        char[] sc = paySign.toCharArray();
        sc[0] = sc[0] == 'a' ? 'b' : 'a';
        tamperedSign.put("sign", new String(sc));
        assertTrue("代收回调篡改 sign 反例", !Signer.verifyCallback(tamperedSign, paySecret));

        // sign 缺失 → 反例
        Map<String, Object> noSign = new LinkedHashMap<>(payCb);
        noSign.remove("sign");
        assertTrue("代收回调缺 sign 反例", !Signer.verifyCallback(noSign, paySecret));

        // 代付回调（用 payout 密钥）
        Map<String, Object> payoutCb = new LinkedHashMap<>();
        payoutCb.put("merchant_no", "M00000001");
        payoutCb.put("payout_no", "WD202501010001");
        payoutCb.put("out_payout_no", "OW202501010001");
        payoutCb.put("amount", 100000L);
        payoutCb.put("currency", "PHP");
        payoutCb.put("status", "success");
        payoutCb.put("fee_amount", 500L);
        payoutCb.put("channel_order_no", null);
        payoutCb.put("finished_at", "2025-01-01T09:00:00+08:00");
        String payoutSign = Signer.sign(payoutCb, payoutSecret);
        payoutCb.put("sign", payoutSign);

        assertTrue("代付回调正例验签通过", Signer.verifyCallback(payoutCb, payoutSecret));
        assertTrue("代付回调错密钥验签失败", !Signer.verifyCallback(payoutCb, paySecret));
        assertTrue("Client.verifyPayoutCallback 正例", client.verifyPayoutCallback(payoutCb));

        Map<String, Object> payoutTampered = new LinkedHashMap<>(payoutCb);
        payoutTampered.put("status", "failed");
        assertTrue("代付回调篡改 status 反例", !Signer.verifyCallback(payoutTampered, payoutSecret));
    }

    // ---------------- 额外：边界/工具 ----------------

    private static void runMiscTests() {
        System.out.println("\n--- 边界/工具 ---");

        // JSON 解析整数为 Long（非 double）→ 字符串化 "10000"
        Object n = Json.parse("10000");
        assertTrue("整数解析为 Long", n instanceof Long);
        assertEquals("Long 字符串化", "10000", n.toString());

        // 大整数 1e12 量级仍为整数文本
        Object big = Json.parse("1000000000000");
        assertEquals("大整数文本", "1000000000000", big.toString());

        // 稳定序列化字符串转义：不转 / 非ASCII <>&，转 " \ 控制字符
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("note", "中文\"<>&/\\\n\t末");
        m.put("tag", "plain");
        String s = Json.stableStringify(m);
        assertEquals("稳定序列化转义",
                "{\"note\":\"中文\\\"<>&/\\\\\\n\\t末\",\"tag\":\"plain\"}", s);

        // 顶层布尔强转 true/false
        Map<String, Object> bp = new LinkedHashMap<>();
        bp.put("inline", Boolean.TRUE);
        bp.put("disabled", Boolean.FALSE);
        bp.put("amount", 1L);
        String base = Signer.buildSignBase(bp, "sk");
        assertEquals("布尔强转 base",
                "amount=1&disabled=false&inline=true&secret=sk", base);

        // inline 归一 1/0
        assertEquals("inline true→1", 1L, Client.normalizeInline(Boolean.TRUE));
        assertEquals("inline false→0", 0L, Client.normalizeInline(Boolean.FALSE));
        assertEquals("inline \"1\"→1", 1L, Client.normalizeInline("1"));
        assertEquals("inline 0→0", 0L, Client.normalizeInline(0));

        // PRODUCTION 无内置基址（按代理专有域名派生，必须显式传 baseUrl）
        assertTrue("PRODUCTION 无内置基址", Environment.PRODUCTION.baseUrl() == null);
        assertEquals("SANDBOX 基址",
                "http://127.0.0.1:3090/api/open/v1",
                Environment.SANDBOX.baseUrl());

        // 选 PRODUCTION 又不传 baseUrl → 抛清晰错误
        boolean threw = false;
        try {
            Config.builder()
                    .environment(Environment.PRODUCTION)
                    .merchantNo("M1").apiKey("k").apiSecretPay("p")
                    .build();
        } catch (IllegalArgumentException e) {
            threw = true;
            assertTrue("PRODUCTION 缺 baseUrl 错误含 baseUrl is required",
                    e.getMessage() != null && e.getMessage().contains("baseUrl is required"));
        }
        assertTrue("PRODUCTION 缺 baseUrl 抛异常", threw);

        // 自定义 baseUrl 覆盖 + 去尾斜杠（PRODUCTION + 显式 baseUrl 生效）
        Config custom = Config.builder()
                .environment(Environment.PRODUCTION)
                .baseUrl("https://api.agent.example.com/api/open/v1/")
                .merchantNo("M1").apiKey("k").apiSecretPay("p")
                .build();
        assertEquals("自定义基址覆盖且去尾斜杠",
                "https://api.agent.example.com/api/open/v1", custom.baseUrl());

        // 仅传 baseUrl（不选 environment）也能生效
        Config baseOnly = Config.builder()
                .baseUrl("https://api.agent.example.com/api/open/v1")
                .merchantNo("M1").apiKey("k").apiSecretPay("p")
                .build();
        assertEquals("仅 baseUrl 生效",
                "https://api.agent.example.com/api/open/v1", baseOnly.baseUrl());

        // null 字段过滤（对齐向量 null_skipped 行为）
        Map<String, Object> np = new LinkedHashMap<>();
        np.put("a", "1");
        np.put("b", null);
        np.put("amount", 500L);
        assertEquals("null 过滤 base",
                "a=1&amount=500&secret=sk", Signer.buildSignBase(np, "sk"));
    }

    // ---------------- 断言工具 ----------------

    private static void assertEquals(String label, Object expected, Object actual) {
        if (expected == null ? actual == null : expected.equals(actual)) {
            passed++;
            System.out.println("  PASS " + label);
        } else {
            failed++;
            System.out.println("  FAIL " + label);
            System.out.println("       expected: " + expected);
            System.out.println("       actual:   " + actual);
        }
    }

    private static void assertTrue(String label, boolean cond) {
        if (cond) {
            passed++;
            System.out.println("  PASS " + label);
        } else {
            failed++;
            System.out.println("  FAIL " + label + " (条件为 false)");
        }
    }

    /** 定位 ../test-vectors.json：优先 cwd 上一级，回退当前目录。 */
    private static Path resolveVectorsPath() {
        Path[] candidates = {
                Path.of("..", "test-vectors.json"),
                Path.of("test-vectors.json"),
                Path.of("..", "..", "test-vectors.json"),
        };
        for (Path p : candidates) {
            if (Files.exists(p)) {
                return p;
            }
        }
        // 默认（让 readAllBytes 抛清晰错误）
        return Path.of("..", "test-vectors.json");
    }
}
