// 示例：回调验签 + 幂等处理 + 正确应答（验签+处理代码片段，非常驻 HTTP 服务）。
//
// 演示两类回调各一次：
//   - 代收回调：用 api_secret_pay 验签；
//   - 代付回调：用 api_secret_payout 验签。
//
// 真实接入时，把 handlePayCallback / handlePayoutCallback 挂到你的 HTTP 路由处理器即可：
// 拿原始 body → 解析 → verifyCallback（时序安全）→ 按 status 幂等处理 → 回 HTTP 200 + 纯文本 success。
//
// 运行：go run ./examples/callback_verify
package main

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"

	"github.com/bebebus/SDK/go"
)

const (
	secretPay    = "sk_test_0123456789abcdef0123456789abcdef"
	secretPayout = "sk_test_0123456789abcdef0123456789abcdef"
)

func main() {
	client := openapi.NewClient(openapi.Config{
		MerchantNo:   "M00000001",
		APIKey:       "ak_demo_key",
		SecretPay:    secretPay,
		SecretPayout: secretPayout,
	})

	// —— 演示 1：构造一段合法的「代收回调」并验签处理 ——
	payBody := signedCallbackBody(map[string]any{
		"merchant_no":      "M00000001",
		"order_no":         "P202501010001",
		"out_order_no":     "202501010001",
		"amount":           10000,
		"actual_amount":    10000,
		"fee_amount":       180,
		"net_amount":       9820,
		"currency":         "PHP",
		"status":           "success",
		"channel_order_no": nil, // 对外恒 null，不参与签名。
		"paid_at":          "2025-01-01T08:00:00+08:00",
	}, secretPay)

	fmt.Println("== 代收回调 ==")
	demoHandle(client.VerifyPayCallback, payBody)

	// —— 演示 2：构造一段合法的「代付回调」并验签处理 ——
	payoutBody := signedCallbackBody(map[string]any{
		"merchant_no":      "M00000001",
		"payout_no":        "W202501010001",
		"out_payout_no":    "WD202501010001",
		"amount":           100000,
		"currency":         "PHP",
		"status":           "success",
		"fee_amount":       500,
		"channel_order_no": nil,
		"finished_at":      "2025-01-01T09:00:00+08:00",
	}, secretPayout)

	fmt.Println("== 代付回调 ==")
	demoHandle(client.VerifyPayoutCallback, payoutBody)
}

// demoHandle 模拟一次回调进入：解析 → 验签 → 幂等处理 → 应答。
func demoHandle(verify func(map[string]any) bool, rawBody []byte) {
	status, reply := processCallback(verify, rawBody)
	fmt.Printf("HTTP %d, body=%q\n\n", status, reply)
}

// processCallback 是可直接复用的核心逻辑：返回应答状态码与文本。
//
//	verify  —— 代收用 client.VerifyPayCallback，代付用 client.VerifyPayoutCallback；
//	rawBody —— 平台 POST 的原始请求体。
func processCallback(verify func(map[string]any) bool, rawBody []byte) (int, string) {
	// 1. 解析（UseNumber 保留整数文本形态，避免大整数被 float64 污染）。
	payload, err := parseJSONObject(rawBody)
	if err != nil {
		// 解析失败：返回非成功，平台会重试。
		return http.StatusBadRequest, "bad request"
	}

	// 2. 时序安全验签（字段无关：除 sign 外全部参与）。
	if !verify(payload) {
		// 验签失败：拒绝处理、不回成功，让平台重试。
		return http.StatusForbidden, "invalid sign"
	}

	// 3. 按 status 幂等处理（同一订单可能被回调多次）。
	status, _ := payload["status"].(string)
	switch status {
	case "success":
		// TODO: 幂等入账/发货。建议以订单号为键加唯一约束或先查后写，重复回调直接跳过。
		fmt.Println("  -> success：幂等入账/发货")
	case "failed":
		// TODO: 幂等标记失败。
		fmt.Println("  -> failed：幂等标记失败")
	default:
		fmt.Printf("  -> 其他状态 %q：按业务处理\n", status)
	}

	// 4. 正确应答：HTTP 200 + 纯文本 success（平台据此判定无需重试）。
	return http.StatusOK, "success"
}

// 真实接入的 HTTP 处理器写法（按需挂到路由）：
//
//	func payCallbackHandler(client *openapi.Client) http.HandlerFunc {
//	    return func(w http.ResponseWriter, r *http.Request) {
//	        raw, _ := io.ReadAll(r.Body)
//	        status, reply := processCallback(client.VerifyPayCallback, raw)
//	        w.WriteHeader(status)
//	        _, _ = io.WriteString(w, reply)
//	    }
//	}

// parseJSONObject 用 Decoder+UseNumber 把 body 解析为键值表。
func parseJSONObject(raw []byte) (map[string]any, error) {
	dec := json.NewDecoder(bytes.NewReader(raw))
	dec.UseNumber()
	var m map[string]any
	if err := dec.Decode(&m); err != nil {
		return nil, err
	}
	return m, nil
}

// signedCallbackBody 仅用于本示例：构造带正确 sign 的回调体（模拟平台发来的报文）。
func signedCallbackBody(fields map[string]any, secret string) []byte {
	// 过滤 nil 后计算 sign（与 SDK 口径一致）。
	signable := map[string]any{}
	for k, v := range fields {
		if v == nil {
			continue
		}
		signable[k] = v
	}
	fields["sign"] = openapi.Sign(signable, secret)

	var buf bytes.Buffer
	enc := json.NewEncoder(&buf)
	enc.SetEscapeHTML(false)
	_ = enc.Encode(fields)
	return buf.Bytes()
}

// 确保 io 被引用（真实处理器读取 r.Body 用到）。
var _ = io.ReadAll
