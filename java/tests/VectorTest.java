import cloud.cniia.openapi.sdk.Json;
import cloud.cniia.openapi.sdk.Signer;
import cloud.cniia.openapi.sdk.SignBinding;
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
        runPayoutFallbackTests();

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
            // v2 向量携 binding（method+path 绑定前缀，1.2.0 起）；v1 向量无该字段 → null
            SignBinding binding = null;
            Object b = v.get("binding");
            if (b instanceof Map) {
                Map<String, Object> bm = (Map<String, Object>) b;
                binding = new SignBinding((String) bm.get("method"), (String) bm.get("path"));
            }

            String actualBase = Signer.buildSignBase(payload, secret, binding);
            String actualSign = Signer.sign(payload, secret, binding);

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
                "http://127.0.0.1:3090/api/open/v2",
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

        Map<String, Object> bindPayload = new LinkedHashMap<>();
        bindPayload.put("a", 1);
        SignBinding binding = new SignBinding("POST", "/api/open/v2/merchant/pay/create");
        assertEquals("v2 binding 前缀",
                "POST\n/api/open/v2/merchant/pay/create\na=1&secret=s",
                Signer.buildSignBase(bindPayload, "s", binding));
        assertEquals("v2 binding sign",
                "1ee55ced40501b30d841a56884eaf8c54f05d080d76868d72b41030eb1ce892b",
                Signer.sign(bindPayload, "s", binding));
        assertEquals("无 binding 保持 v1",
                "a=1&secret=s", Signer.buildSignBase(bindPayload, "s"));
    }

    // ---------------- 代付 v1 回落（契约：代付仅存在于 v1，2026-08-15 拍板） ----------------

    /**
     * 用 JDK 内建 HttpServer 起本地桩，验证：
     * <ul>
     *   <li>v2 baseUrl 下 payout 请求自动回落 v1 路径，且签名为 body-only（与独立复算一致）；</li>
     *   <li>v2 baseUrl 下 pay 请求仍打 v2 路径并携带 METHOD+path 绑定签名（防回归反向锚）。</li>
     * </ul>
     */
    private static void runPayoutFallbackTests() throws Exception {
        System.out.println("\n--- 代付 v1 回落（v2 基址） ---");
        String paySecret = "sk_pay_secret_aaaaaaaaaaaaaaaaaaaaaaaa";
        String payoutSecret = "sk_payout_secret_bbbbbbbbbbbbbbbbbbbb";

        final String[] capturedPath = new String[1];
        final String[] capturedBody = new String[1];
        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(
                new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            capturedPath[0] = exchange.getRequestURI().getPath();
            capturedBody[0] = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            byte[] resp = "{\"code\":0,\"message\":\"ok\",\"data\":{}}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, resp.length);
            exchange.getResponseBody().write(resp);
            exchange.close();
        });
        server.start();
        try {
            int port = server.getAddress().getPort();
            Config cfg = Config.builder()
                    .baseUrl("http://127.0.0.1:" + port + "/api/open/v2")
                    .merchantNo("M00000001").apiKey("ak")
                    .apiSecretPay(paySecret).apiSecretPayout(payoutSecret)
                    .build();
            Client client = new Client(cfg);

            // 1) payout：v2 基址下自动回落 v1 + body-only 签名
            Map<String, Object> payoutReq = new LinkedHashMap<>();
            payoutReq.put("payout_no", "W1");
            client.payoutQuery(payoutReq);
            assertEquals("payout 回落 v1 路径", "/api/open/v1/merchant/payout/query", capturedPath[0]);
            Map<String, Object> payoutSent = Json.parseObject(capturedBody[0]);
            String payoutSign = (String) payoutSent.remove("sign");
            assertEquals("payout body-only 签名（独立复算一致）",
                    Signer.sign(payoutSent, payoutSecret), payoutSign);
            // 反向锚：若仍按 v2 绑定基串计算则必不相等
            assertTrue("payout 反例（v2 绑定签名必不相等）",
                    !Signer.sign(payoutSent, payoutSecret,
                            new SignBinding("POST", "/api/open/v2/merchant/payout/query")).equals(payoutSign));

            // 2) pay：仍打 v2 + 绑定签名（防回归反向锚）
            Map<String, Object> payReq = new LinkedHashMap<>();
            payReq.put("order_no", "P1");
            client.payQuery(payReq);
            assertEquals("pay 仍打 v2 路径", "/api/open/v2/merchant/pay/query", capturedPath[0]);
            Map<String, Object> paySent = Json.parseObject(capturedBody[0]);
            String paySign = (String) paySent.remove("sign");
            assertEquals("pay v2 绑定签名（独立复算一致）",
                    Signer.sign(paySent, paySecret,
                            new SignBinding("POST", "/api/open/v2/merchant/pay/query")), paySign);
            assertTrue("pay 反例（body-only 签名必不相等）",
                    !Signer.sign(paySent, paySecret).equals(paySign));
        } finally {
            server.stop(0);
        }
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
