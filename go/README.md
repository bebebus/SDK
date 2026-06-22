# 商户支付 OpenAPI · Go SDK

[![Go Reference](https://pkg.go.dev/badge/github.com/bebebus/SDK/go.svg)](https://pkg.go.dev/github.com/bebebus/SDK/go) [![License: MIT](https://img.shields.io/badge/license-MIT-green)](LICENSE)

零第三方依赖的 Go SDK，覆盖商户 OpenAPI 全部 11 个端点（代收 5 + 代付 6），含
HMAC-SHA256 签名、双环境配置、回调时序安全验签。仅用 Go 标准库（`net/http` / `crypto/hmac`
/ `crypto/sha256` / `encoding/json`），**无任何 `require`**。

- module：`github.com/bebebus/SDK/go`
- package：`openapi`
- Go：1.26（`go.mod` 声明，向下兼容 1.21+ 的标准库特性）

## 引入（无需安装依赖）

本 SDK 不依赖任何外部包。两种用法：

1. 直接拷贝本 `go/` 目录到你的工程并按模块路径导入；或
2. 在自己的 `go.mod` 里 `require github.com/bebebus/SDK/go v0.0.0` 后用 `replace`
   指向本地路径（私有未发布时）：

```go
import "github.com/bebebus/SDK/go"
```

## 快速开始

```go
package main

import (
    "context"
    "errors"
    "fmt"

    "github.com/bebebus/SDK/go"
)

func main() {
    client := openapi.NewClient(openapi.Config{
        MerchantNo:   "M00000001",
        APIKey:       "ak_demo_key",
        SecretPay:    "<api_secret_pay>",    // 代收类 + 代收/退款回调
        SecretPayout: "<api_secret_payout>", // 代付类 + 代付回调
        Environment:  openapi.Sandbox,       // 或 openapi.Production（须显式传 BaseURL）
    })

    resp, err := client.PayCreate(context.Background(), map[string]any{
        "out_order_no": "202501010001",
        "amount":       10000, // 最小单位整数，10000 = 1 元
        "currency":     "PHP",
        "pay_method":   "gcash",
        "country":      "PH",
        "notify_url":   "https://merchant.example.com/api/notify/pay",
    })
    if err != nil {
        var apiErr *openapi.APIError
        if errors.As(err, &apiErr) {
            fmt.Println("业务失败:", apiErr.Code, apiErr.Message)
            return
        }
        fmt.Println("传输失败:", err) // *openapi.TransportError
        return
    }
    fmt.Println("order_no:", resp.Data["order_no"])
}
```

### 11 个端点方法

| 类别 | 方法 | 端点 | 密钥 |
|------|------|------|------|
| 代收 | `PayCreate` | `/merchant/pay/create` | pay |
| 代收 | `PayQuery` | `/merchant/pay/query` | pay |
| 代收 | `PayMethodsQuery` | `/merchant/pay-methods/query` | pay |
| 代收 | `BalanceQuery` | `/merchant/balance/query` | pay |
| 代收 | `PayTestComplete` | `/merchant/pay/test/complete` | pay |
| 代付 | `PayoutCreate` | `/merchant/payout/create` | payout |
| 代付 | `PayoutQuery` | `/merchant/payout/query` | payout |
| 代付 | `PayoutBanksQuery` | `/merchant/payout/banks/query` | payout |
| 代付 | `PayoutProofQuery` | `/merchant/payout/proof/query` | payout |
| 代付 | `PayoutReceiptQuery` | `/merchant/payout/receipt/query` | payout |
| 代付 | `PayoutTestComplete` | `/merchant/payout/test/complete` | payout |

每个方法自动注入通用字段（`merchant_no` / `api_key` / `timestamp` / 唯一 `nonce`）与 `sign`，并
自动选对密钥。**值为 `nil` 的字段不会进入请求体也不参与签名**。`PayoutReceiptQuery` 的 `inline`
作为独立布尔参数传入，SDK 自动以**整数 1/0** 发送（避免布尔签名歧义）。

## 双环境配置

```go
// 预设：正式（无内置 URL，必须显式传 BaseURL）
openapi.NewClient(openapi.Config{Environment: openapi.Production, BaseURL: "https://api.<agent_domain>/api/open/v1", /* ... */})
// 预设：本地沙箱（http://127.0.0.1:3090/api/open/v1）
openapi.NewClient(openapi.Config{Environment: openapi.Sandbox, /* ... */})
// 自定义基址覆盖（代理专有域名或其它端口）——优先于 Environment
openapi.NewClient(openapi.Config{BaseURL: "https://api.<agent_domain>/api/open/v1", /* ... */})
```

- `Production` → **无内置 URL**。正式基址按上级代理专有域名派生，形如
  `https://api.<agent_domain>/api/open/v1`，必须用 `Config.BaseURL` 显式提供。
  若选 `Production` 又不传 `BaseURL`，`NewClient` 不 panic，但首个请求返回
  `ErrBaseURLRequired`（清晰报错）。
- `Sandbox` → `http://127.0.0.1:3090/api/open/v1`
- `Config.Timeout` 默认 30s。

## 回调验签与应答

回调按「除 `sign` 外所有字段参与签名」通用计算（字段无关，平台可增删字段），比较使用
`hmac.Equal` 时序安全。代收/退款回调用 `VerifyPayCallback`，代付回调用 `VerifyPayoutCallback`。

```go
raw, _ := io.ReadAll(r.Body)

dec := json.NewDecoder(bytes.NewReader(raw))
dec.UseNumber() // 保留整数文本形态，避免大整数被 float64 写成 1e+12
var payload map[string]any
_ = dec.Decode(&payload)

if !client.VerifyPayCallback(payload) { // 代付回调改用 VerifyPayoutCallback
    w.WriteHeader(http.StatusForbidden) // 验签失败：不回成功，让平台重试
    return
}

switch payload["status"] {
case "success":
    // 幂等入账/发货（同一订单可能被回调多次）
case "failed":
    // 幂等标记失败
}

w.WriteHeader(http.StatusOK)
io.WriteString(w, "success") // 正确应答：HTTP 200 + 纯文本 success
```

完整可运行示例见 `examples/callback_verify`（代收 + 代付各演示一次）。

## 签名（可单独取用）

```go
base := openapi.BuildSignBase(payload, secret) // 便于逐字节断言
sign := openapi.Sign(payload, secret)
ok := openapi.VerifyCallback(payload, secret)  // 时序安全
```

序列化口径与服务端签名实现逐字节一致：嵌套 JSON 自写 key 升序递归、
紧凑无空格、不转义 `/` 与非 ASCII 与 `<>&`、只转 `" \ \b\f\n\r\t` 及其余控制字符；顶层标量原样
（`bool→true/false`、整数十进制）。线缆序列化用 `Encoder.SetEscapeHTML(false)`。

## 错误处理

- 业务失败（信封 `code != 0`）→ `*openapi.APIError`（含 `Code`/`Message`/`Data`/`Raw`）。
- HTTP/网络/非 2xx/解析失败 → `*openapi.TransportError`（含 `StatusCode`/`Raw`/`Err`，可 `errors.Unwrap`）。
- 即使返回错误，`*Response.Raw` 仍保留原始响应体，便于排查。

用 `errors.As` 区分：

```go
var apiErr *openapi.APIError
var trErr  *openapi.TransportError
switch {
case errors.As(err, &apiErr):  // 业务码
case errors.As(err, &trErr):   // 传输层
}
```

## 运行示例

```bash
go run ./examples/pay_create
go run ./examples/payout_create
go run ./examples/callback_verify   # 离线演示验签+处理+应答，无需联网
go run ./examples/query
```

## 跑测试

```bash
cd go
go test ./...          # 全部
go test -v ./...       # 详细（含每个签名向量子用例）
```

单测会读取 `../test-vectors.json`，对每个向量断言 `BuildSignBase == base` 且 `Sign == sign`，
并覆盖回调验签正例 + 篡改一字节反例 + 错误密钥反例，以及客户端的通用字段注入、密钥选择、
`inline` 整数化、业务/传输错误分类。使用标准库 `testing`，无第三方测试框架。
