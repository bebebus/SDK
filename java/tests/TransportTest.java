import cloud.cniia.openapi.sdk.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** 使用回环 HTTP 服务测试请求签名、业务错误和传输错误。 */
public final class TransportTest {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        AtomicReference<String> response = new AtomicReference<>("{\"code\":0,\"data\":{\"order_no\":\"P1\"}}");
        AtomicInteger status = new AtomicInteger(200);
        AtomicReference<String> captured = new AtomicReference<>();
        AtomicReference<String> path = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            path.set(exchange.getRequestURI().getPath());
            captured.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = response.get().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status.get(), bytes.length);
            try (var body = exchange.getResponseBody()) { body.write(bytes); }
        });
        server.start();
        try {
            Config config = Config.builder().baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
                    .merchantNo("M1").apiKey("key").apiSecretPay("pay-secret")
                    .apiSecretPayout("payout-secret").build();
            Client client = new Client(config);
            ApiResponse result = client.payQuery(Map.of("out_order_no", "merchant-order"));
            check(result.isSuccess() && "P1".equals(result.dataAsMap().get("order_no")), "成功响应");
            check("/merchant/pay/query".equals(path.get()), "请求路径");
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>) Json.parse(captured.get());
            check("M1".equals(payload.get("merchant_no")), "商户号");
            check("merchant-order".equals(payload.get("out_order_no")), "商户单号");
            check(Signer.sign(payload, "pay-secret").equals(payload.get("sign")), "代收签名");
            check(result.requestSign().equals(payload.get("sign")), "返回请求签名");
            client.payoutQuery(Map.of("out_payout_no", "wd1"));
            @SuppressWarnings("unchecked")
            Map<String, Object> payout = (Map<String, Object>) Json.parse(captured.get());
            check("/merchant/payout/query".equals(path.get()), "代付请求路径");
            check(Signer.sign(payout, "payout-secret").equals(payout.get("sign")), "代付签名");
            String[] expectedPaths = {
                "/merchant/pay/create", "/merchant/pay-methods/query", "/merchant/countries/query",
                "/merchant/groups/query", "/merchant/balance/query", "/merchant/pay/test/complete",
                "/merchant/payout/create", "/merchant/payout/banks/query", "/merchant/payout/proof/query",
                "/merchant/payout/receipt/query", "/merchant/payout/test/complete"
            };
            Runnable[] calls = {
                () -> client.payCreate(Map.of()), () -> client.payMethodsQuery(Map.of()),
                () -> client.countriesQuery(), () -> client.groupsQuery(Map.of()),
                () -> client.balanceQuery(Map.of()), () -> client.payTestComplete(Map.of()),
                () -> client.payoutCreate(Map.of()), () -> client.payoutBanksQuery(Map.of()),
                () -> client.payoutProofQuery(Map.of()),
                () -> client.payoutReceiptQuery(Map.of("inline", true)),
                () -> client.payoutTestComplete(Map.of())
            };
            for (int i = 0; i < calls.length; i++) {
                calls[i].run();
                check(expectedPaths[i].equals(path.get()), "端点路径: " + expectedPaths[i]);
                @SuppressWarnings("unchecked")
                Map<String, Object> request = (Map<String, Object>) Json.parse(captured.get());
                String secret = i < 6 ? "pay-secret" : "payout-secret";
                check(Signer.sign(request, secret).equals(request.get("sign")), "端点签名: " + expectedPaths[i]);
                if (i == 9) check(Long.valueOf(1).equals(request.get("inline")), "收据 inline 归一化");
            }
            response.set("{\"code\":123,\"message\":\"declined\",\"data\":{\"reason\":\"x\"}}");
            try {
                client.payQuery(Map.of());
                throw new AssertionError("业务错误应抛异常");
            } catch (ApiException e) {
                check(e.code() == 123 && "x".equals(e.dataAsMap().get("reason")), "业务错误信息");
            }
            check(client.callRaw("/merchant/pay/query", Map.of(), false).code() == 123, "原始信封");
            status.set(503);
            response.set("retry");
            try {
                client.payQuery(Map.of());
                throw new AssertionError("HTTP 错误应抛异常");
            } catch (TransportException e) {
                check(e.statusCode() == 503 && "retry".equals(e.rawBody()), "HTTP 错误信息");
            }
            status.set(200);
            for (String body : new String[] {"bad-json", "[]", "{}", "{\"code\":0.5}"}) {
                response.set(body);
                try {
                    client.payQuery(Map.of());
                    throw new AssertionError("无效信封应抛异常: " + body);
                } catch (TransportException e) {
                    check(body.equals(e.rawBody()), "保留无效响应");
                }
            }
            System.out.println("TransportTest 通过");
        } finally {
            server.stop(0);
        }
    }
}
