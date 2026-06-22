package projectp

import (
	"bytes"
	"context"
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"
)

// TestEnvironmentBaseURL 校验预设基址与自定义覆盖。
func TestEnvironmentBaseURL(t *testing.T) {
	if got := Production.BaseURL(); got != productionBaseURL {
		t.Errorf("production 基址错误: %s", got)
	}
	if got := Sandbox.BaseURL(); got != sandboxBaseURL {
		t.Errorf("sandbox 基址错误: %s", got)
	}

	c := NewClient(Config{Environment: Sandbox})
	if c.baseURL != sandboxBaseURL {
		t.Errorf("sandbox client 基址错误: %s", c.baseURL)
	}

	custom := "https://api.agent.example.com/api/open/v1"
	c2 := NewClient(Config{Environment: Production, BaseURL: custom})
	if c2.baseURL != custom {
		t.Errorf("自定义基址未覆盖预设: %s", c2.baseURL)
	}
}

// TestBuildBodyInjectsCommonFields 校验通用字段、nonce、timestamp、sign 注入与 nil 过滤。
func TestBuildBodyInjectsCommonFields(t *testing.T) {
	// 固定时钟与 nonce 便于断言。
	origNow, origNonce := nowUnix, newNonce
	nowUnix = func() int64 { return 1736073600 }
	newNonce = func() string { return "fixed_nonce" }
	defer func() { nowUnix, newNonce = origNow, origNonce }()

	c := NewClient(Config{
		MerchantNo: "M00000001",
		APIKey:     "ak_demo_key",
		SecretPay:  "sk_test_0123456789abcdef0123456789abcdef",
	})

	body := c.buildBody(map[string]any{
		"out_order_no": "202501010001",
		"amount":       10000,
		"return_url":   nil, // 应被过滤。
	}, c.cfg.SecretPay)

	if body["merchant_no"] != "M00000001" || body["api_key"] != "ak_demo_key" {
		t.Errorf("通用字段缺失: %+v", body)
	}
	if body["timestamp"] != int64(1736073600) {
		t.Errorf("timestamp 未注入: %+v", body["timestamp"])
	}
	if body["nonce"] != "fixed_nonce" {
		t.Errorf("nonce 未注入: %+v", body["nonce"])
	}
	if _, ok := body["return_url"]; ok {
		t.Errorf("nil 字段未被过滤")
	}
	sign, ok := body["sign"].(string)
	if !ok || len(sign) != 64 {
		t.Errorf("sign 未正确注入: %+v", body["sign"])
	}
	// sign 应等于对去掉 sign 后的 body 重新计算的值（自洽）。
	verifyBody := cloneParams(body)
	if !VerifyCallback(verifyBody, c.cfg.SecretPay) {
		t.Errorf("注入的 sign 与重算不一致")
	}
}

// TestCallSecretSelection 校验 pay 用 SecretPay、payout 用 SecretPayout。
func TestCallSecretSelection(t *testing.T) {
	c := NewClient(Config{SecretPay: "pay_secret", SecretPayout: "payout_secret"})
	if c.secretFor(usePay) != "pay_secret" {
		t.Errorf("pay 密钥选择错误")
	}
	if c.secretFor(usePayout) != "payout_secret" {
		t.Errorf("payout 密钥选择错误")
	}
}

// TestReceiptInlineAsInteger 校验 inline 以整数 1/0 发送。
func TestReceiptInlineAsInteger(t *testing.T) {
	var captured map[string]any
	srv := newTestServer(t, &captured, `{"code":0,"message":"ok","data":{"receipt_url":"u"}}`)
	defer srv.Close()

	c := NewClient(Config{BaseURL: srv.URL, SecretPayout: "s"})

	if _, err := c.PayoutReceiptQuery(context.Background(), map[string]any{"payout_no": "W1"}, true); err != nil {
		t.Fatalf("inline=true 调用失败: %v", err)
	}
	if n, ok := captured["inline"].(json.Number); !ok || n.String() != "1" {
		t.Errorf("inline=true 应以整数 1 发送，实际: %+v (%T)", captured["inline"], captured["inline"])
	}

	if _, err := c.PayoutReceiptQuery(context.Background(), map[string]any{"payout_no": "W1"}, false); err != nil {
		t.Fatalf("inline=false 调用失败: %v", err)
	}
	if n, ok := captured["inline"].(json.Number); !ok || n.String() != "0" {
		t.Errorf("inline=false 应以整数 0 发送，实际: %+v (%T)", captured["inline"], captured["inline"])
	}
}

// TestCallBusinessError 校验 code!=0 抛 APIError 并携带 code/message/data。
func TestCallBusinessError(t *testing.T) {
	var captured map[string]any
	srv := newTestServer(t, &captured,
		`{"code":100001,"message":"\"amount\" must be a number","data":{"missing_fields":["amount"]}}`)
	defer srv.Close()

	c := NewClient(Config{BaseURL: srv.URL, SecretPay: "s"})
	resp, err := c.PayCreate(context.Background(), map[string]any{"out_order_no": "x"})
	if err == nil {
		t.Fatalf("应返回业务错误")
	}
	apiErr, ok := err.(*APIError)
	if !ok {
		t.Fatalf("应为 *APIError，实际 %T", err)
	}
	if apiErr.Code != 100001 {
		t.Errorf("code 错误: %d", apiErr.Code)
	}
	if apiErr.Data["missing_fields"] == nil {
		t.Errorf("data 未携带")
	}
	// 即便出错，也应能拿到原始响应。
	if resp == nil || len(resp.Raw) == 0 {
		t.Errorf("应保留原始响应途径")
	}
}

// TestCallTransportError 校验非 2xx 抛 TransportError。
func TestCallTransportError(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusBadGateway)
		_, _ = io.WriteString(w, "upstream down")
	}))
	defer srv.Close()

	c := NewClient(Config{BaseURL: srv.URL, SecretPay: "s", Timeout: 5 * time.Second})
	_, err := c.PayQuery(context.Background(), map[string]any{"order_no": "x"})
	if err == nil {
		t.Fatalf("应返回传输错误")
	}
	tErr, ok := err.(*TransportError)
	if !ok {
		t.Fatalf("应为 *TransportError，实际 %T", err)
	}
	if tErr.StatusCode != http.StatusBadGateway {
		t.Errorf("状态码错误: %d", tErr.StatusCode)
	}
}

// TestCallSuccessParsesData 校验成功响应解析 data 且大整数不被科学计数法污染。
func TestCallSuccessParsesData(t *testing.T) {
	var captured map[string]any
	srv := newTestServer(t, &captured,
		`{"code":0,"message":"ok","data":{"order_no":"P1","amount":1000000000000,"status":"pending"}}`)
	defer srv.Close()

	c := NewClient(Config{BaseURL: srv.URL, SecretPay: "s"})
	resp, err := c.PayCreate(context.Background(), map[string]any{"out_order_no": "x", "amount": 1000000000000})
	if err != nil {
		t.Fatalf("调用失败: %v", err)
	}
	amt, ok := resp.Data["amount"].(json.Number)
	if !ok || amt.String() != "1000000000000" {
		t.Errorf("大整数解析错误: %+v (%T)", resp.Data["amount"], resp.Data["amount"])
	}
}

// newTestServer 返回一个回放固定响应、并把请求体捕获到 captured 的测试服务器。
func newTestServer(t *testing.T, captured *map[string]any, response string) *httptest.Server {
	t.Helper()
	return httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		raw, _ := io.ReadAll(r.Body)
		dec := json.NewDecoder(bytes.NewReader(raw))
		dec.UseNumber()
		var m map[string]any
		_ = dec.Decode(&m)
		*captured = m
		w.Header().Set("Content-Type", "application/json")
		_, _ = io.WriteString(w, response)
	}))
}
