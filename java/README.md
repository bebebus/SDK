> 中文 | [English](./README.en.md)

# 商户支付 OpenAPI — Java SDK

零第三方依赖的 Java 客户端，覆盖商户支付 OpenAPI 全部 **11 个端点**，实现 HMAC-SHA256 请求签名与回调验签。

- **JDK**：17+（用 `java.net.http.HttpClient`、`javax.crypto.Mac`、`MessageDigest.isEqual`，全为标准库）。
- **依赖**：无。HTTP、JSON、HMAC、测试全部用 JDK 内建，**不引入任何包管理器拉取的库**（无 Jackson/Gson/OkHttp/JUnit）。
- **包名**：`cloud.cniia.openapi.sdk`。

签名算法与字段契约见仓库根目录的 [`SIGNING.md`](../SIGNING.md) 与 [`INTERFACES.md`](../INTERFACES.md)，签名标准答案见 [`test-vectors.json`](../test-vectors.json)。

## 如何引入（源码引入，**不发 Maven**）

> 本 Java SDK **不上架 Maven Central**，仅以源码方式引入（与 npm/PyPI/Packagist/Go 的上架口径区分）。`release.sh` 的发布闭环不含 Java。

本 SDK 没有外部依赖，最简单的方式就是把源码目录直接纳入你的工程：

```
src/main/java/cloud/cniia/openapi/sdk/*.java
```

把这 7 个源文件（`Signer / Config / Environment / Client / Json / ApiException / TransportException`，外加 `ApiResponse`）加入你的编译源即可。

或者用 `javac` 编译成 classes / jar：

```bash
# 编译到 out/
javac --release 17 -encoding UTF-8 -d out $(find src/main/java -name '*.java')
# 打成 jar
jar cf openapi-sdk.jar -C out .
```

也提供了 [`pom.xml`](./pom.xml) 供你**在本地**用 Maven 打 jar（`<dependencies>` 故意为空）——仅供自行构建，**不用于发布到 Maven Central**。**测试不依赖 Maven**。

## 快速开始

```java
import cloud.cniia.openapi.sdk.*;
import java.util.*;

Config config = Config.builder()
        .environment(Environment.SANDBOX)          // 或 Environment.PRODUCTION
        .merchantNo("M00000001")
        .apiKey("ak_demo_key")
        .apiSecretPay("sk_pay_xxx")                // pay 类接口 / 代收回调用
        .apiSecretPayout("sk_payout_xxx")          // payout 类接口 / 代付回调用
        .timeout(java.time.Duration.ofSeconds(30)) // 可选，默认 30s
        .build();

Client client = new Client(config);

Map<String, Object> params = new LinkedHashMap<>();
params.put("out_order_no", "202501010001");
params.put("amount", 10000L);                      // 最小单位整数：10000 = 1 元
params.put("currency", "PHP");
params.put("pay_method", "gcash");
params.put("country", "PH");
params.put("notify_url", "https://merchant.example.com/api/notify/pay");

ApiResponse resp = client.payCreate(params);       // 自动注入 timestamp/nonce、选 pay 密钥签名
System.out.println(resp.dataAsMap().get("order_no"));
```

### 全部 11 个端点（客户端方法）

| 业务 | 方法 | 端点 | 密钥 |
|------|------|------|------|
| 代收下单 | `payCreate` | `/merchant/pay/create` | pay |
| 代收查单 | `payQuery` | `/merchant/pay/query` | pay |
| 支付方式 | `payMethodsQuery` | `/merchant/pay-methods/query` | pay |
| 余额 | `balanceQuery` | `/merchant/balance/query` | pay |
| 代收测试完成 | `payTestComplete` | `/merchant/pay/test/complete` | pay（仅测试密钥）|
| 代付下单 | `payoutCreate` | `/merchant/payout/create` | payout |
| 代付查单 | `payoutQuery` | `/merchant/payout/query` | payout |
| 代付银行 | `payoutBanksQuery` | `/merchant/payout/banks/query` | payout |
| 代付凭证 | `payoutProofQuery` | `/merchant/payout/proof/query` | payout |
| 代付收据 | `payoutReceiptQuery` | `/merchant/payout/receipt/query` | payout |
| 代付测试完成 | `payoutTestComplete` | `/merchant/payout/test/complete` | payout（仅测试密钥）|

每个方法都接受 `Map<String, Object>` 业务字段：

- **金额用整数**（`Long`/`Integer`），`10000 = 1 元`，避免浮点跨语言差异。
- **值为 `null` 的字段不会发送、也不参与签名**。
- 通用字段 `merchant_no` / `api_key` / `timestamp`（Unix 秒）/ `nonce`（每请求唯一）由 SDK **自动注入**。
- `receipt/query` 的 `inline` 可传布尔，SDK 自动归一为**整数 1/0** 后签名发送。
- 嵌套 `extra`（如 `customer`）传 `Map`/`List`，按稳定 JSON 序列化参与签名。

### 错误处理

- `code != 0` → 抛 `ApiException`（携带 `code()` / `message` / `data()` / `rawBody()`）。
- HTTP/网络错误、非 2xx、响应体非 JSON → 抛 `TransportException`（携带 `statusCode()` / `rawBody()`）。
- 想自行判码不抛业务异常：用 `client.callRaw(path, params, isPayout)` 拿 `ApiResponse`（含 `code()` / `data()` / `rawBody()`）。

### 双环境与自定义基址

```java
// PRODUCTION 无内置基址：正式地址请向服务商获取，必须显式传 baseUrl
Config.builder()
      .environment(Environment.PRODUCTION)
      .baseUrl("https://api.<service_domain>/api/open/v1")        // 必填，否则 build() 抛错
      .merchantNo("M00000001").apiKey("ak").apiSecretPay("...")
      .build();

// SANDBOX 使用内置本地地址
Config.builder().environment(Environment.SANDBOX) ...           // http://127.0.0.1:3090/api/open/v1

// 自定义基址覆盖：优先级高于 environment，尾斜杠会被去掉
Config.builder()
      .baseUrl("https://api.<service_domain>/api/open/v1")
      .merchantNo("M00000001").apiKey("ak").apiSecretPay("...")
      .build();
```

> 选 `PRODUCTION` 而不传 `baseUrl` 时，`build()` 会抛 `IllegalArgumentException`
> （`baseUrl is required: obtain the production URL from your service provider ...`）。

### 回调验签（验签 + 处理片段，非常驻服务）

```java
// 在你的 HTTP handler 里：rawBody 是收到的原始回调请求体
Map<String, Object> payload = Json.parseObject(rawBody);

// 时序安全验签：代收/退款用 pay 密钥，代付用 payout 密钥（密钥由 SDK 自动选）
if (!client.verifyPayCallback(payload)) {       // 代收/退款回调
    return /* 非 success 体或非 2xx；同一订单可能再次收到回调 */;
}
String status = String.valueOf(payload.get("status"));
if ("success".equals(status)) {
    // 幂等入账（同一订单可能被回调多次）
} else if ("failed".equals(status)) {
    // 幂等标记失败
}
return "success"; // HTTP 200 + 纯文本 success
```

代付回调同理，改用 `client.verifyPayoutCallback(payload)`。验签器**只依赖"除 sign 外所有字段参与"通用规则**，不硬编码字段表，回调字段新增或减少也兼容。完整可运行示例见 [`examples/CallbackVerifyExample.java`](./examples/CallbackVerifyExample.java)（代收 + 代付各演示一次，含篡改反例）。

## examples/

| 文件 | 内容 |
|------|------|
| `PayCreateExample.java` | 代收下单（含 extra 嵌套对象） |
| `PayoutCreateExample.java` | 代付下单（先查银行编码再下单） |
| `CallbackVerifyExample.java` | 回调验签 + 幂等处理 + 应答（代收 + 代付，含篡改反例） |
| `QueryExample.java` | 查单 / 余额 / 支付方式 / 收据（inline 演示） |

运行任一示例（在 `java/` 目录下）：

```bash
javac --release 17 -encoding UTF-8 -d out $(find src/main/java -name '*.java') examples/CallbackVerifyExample.java
java -Dfile.encoding=UTF-8 -cp out CallbackVerifyExample
```

## 如何跑测试

```bash
cd java
bash run-tests.sh
```

`run-tests.sh` 用 `javac` 把 `src/main/java` + `tests` 编译到 `out/`，再用 `java` 运行断言 runner `VectorTest`：

- **(a) 签名向量**：读取 `../test-vectors.json`，对每个向量断言 `buildSignBase == base` 且 `sign == sign`。
- **(b) 回调验签**：代收 + 代付正例验签通过；篡改字段 / 篡改 sign / 缺 sign / 错密钥 均为反例。
- 额外断言：JSON 整数解析为 `Long`（非 double）、字符串转义边界、布尔强转 `true/false`、`inline` 归一、环境基址与自定义覆盖、`null` 过滤。

任一断言失败 → runner 非 0 退出 → 脚本非 0 退出（可直接接 CI）。

## 实现要点（签名一致性）

- **整数不退化为浮点**：JSON 解析把整数 token 解成 `Long`/`BigInteger`，保证 `String` 化为 `"10000"` 而非 `"10000.0"` / `"1e+12"`。
- **字符串转义对齐 JS `JSON.stringify`**：嵌套字符串只转 `" \ \b\f\n\r\t` 及其余 `U+0000–U+001F` 控制字符（小写四位 hex）；**不转** `/`、非 ASCII、`<>&`。顶层标量直接转字符串、不加引号。
- **HMAC**：`HmacSHA256`，UTF-8 编码，输出十六进制小写；HMAC key 与 base 末尾 `&secret=` 是同一个 secret。
- **回调验签时序安全**：`MessageDigest.isEqual` 比较期望与回调 sign。
