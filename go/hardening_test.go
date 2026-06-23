package openapi

import (
	"context"
	"encoding/json"
	"errors"
	"math"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

// ===================== [A] 空密钥 fail-closed =====================

// TestSignPanicsOnBlankSecret 校验：空/全空白密钥签名时 panic（禁止空密钥签名）。
func TestSignPanicsOnBlankSecret(t *testing.T) {
	for _, secret := range []string{"", "   ", "\t\n"} {
		func() {
			defer func() {
				if r := recover(); r == nil {
					t.Errorf("空密钥 %q 应 panic", secret)
				}
			}()
			_ = Sign(map[string]any{"amount": 1}, secret)
		}()
	}
}

// TestVerifyCallbackRejectsBlankSecret 校验：空密钥验签在算 HMAC 前直接 false。
func TestVerifyCallbackRejectsBlankSecret(t *testing.T) {
	payload := map[string]any{"amount": json.Number("1")}
	// 用一个合法密钥先算出正确 sign。
	payload["sign"] = Sign(cloneParams(payload), "real_secret")
	for _, secret := range []string{"", "   ", "\t"} {
		if VerifyCallback(payload, secret) {
			t.Errorf("空密钥 %q 验签必须返回 false", secret)
		}
	}
}

// ===================== [B] 验签异常一律归 false（绝不冒泡） =====================

// TestVerifyCallbackNilPayload 校验：nil 回调体直接 false，不 panic。
func TestVerifyCallbackNilPayload(t *testing.T) {
	if VerifyCallback(nil, "s") {
		t.Fatalf("nil 回调体应返回 false")
	}
}

// TestVerifyCallbackInvalidSign 校验：sign 非字符串/长度异常/含非法字符一律 false。
func TestVerifyCallbackInvalidSign(t *testing.T) {
	secret := "sk_test_0123456789abcdef0123456789abcdef"
	base := map[string]any{"amount": json.Number("1"), "status": "success"}

	cases := []any{
		12345,                  // 非字符串
		true,                   // 非字符串
		"",                     // 空串
		"abc",                  // 长度不足
		strings.Repeat("a", 63), // 长度 63
		strings.Repeat("a", 65), // 长度 65
		strings.Repeat("z", 64), // 64 位但含非 hex 字符 z
		"../../etc/passwd",      // 攻击者可控的非 hex
	}
	for _, badSign := range cases {
		p := cloneParams(base)
		p["sign"] = badSign
		if VerifyCallback(p, secret) {
			t.Errorf("非法 sign %v(%T) 应返回 false", badSign, badSign)
		}
	}
}

// TestVerifyCallbackRecoversNumericPanic 校验：回调体含非法数值（如非整数浮点）时，
// 验签 recover 为 false 而非 panic 冒泡。
func TestVerifyCallbackRecoversNumericPanic(t *testing.T) {
	secret := "sk_test_0123456789abcdef0123456789abcdef"
	// 构造一个 64 位合法 hex 的 sign（值无所谓，反正算签名时会 panic 被 recover）。
	payload := map[string]any{
		"amount": 1.5, // 非整数浮点：Sign 内部会 panic。
		"sign":   strings.Repeat("0", 64),
	}
	got := func() (ok bool) {
		defer func() {
			if r := recover(); r != nil {
				t.Fatalf("VerifyCallback 不应让 panic 冒泡，实际: %v", r)
			}
		}()
		return VerifyCallback(payload, secret)
	}()
	if got {
		t.Fatalf("非法数值回调体应验签失败")
	}
}

// ===================== [C] 数值规范：拒绝 NaN/Infinity/非整数浮点，-0→"0" =====================

func TestSignRejectsNonIntegerFloat(t *testing.T) {
	assertSignPanics(t, map[string]any{"amount": 1.5}, "非整数浮点应 panic")
}

func TestSignRejectsNaNInfinity(t *testing.T) {
	assertSignPanics(t, map[string]any{"x": math.NaN()}, "NaN 应 panic")
	assertSignPanics(t, map[string]any{"x": math.Inf(1)}, "+Infinity 应 panic")
	assertSignPanics(t, map[string]any{"x": math.Inf(-1)}, "-Infinity 应 panic")
}

func TestSignNegativeZeroNormalizesToZero(t *testing.T) {
	secret := "s"
	negZero := func() float64 { z := 0.0; return -z }()
	base := BuildSignBase(map[string]any{"count": negZero}, secret)
	if !strings.Contains(base, "count=0&") {
		t.Errorf("-0 应归一为 \"0\"，实际 base: %s", base)
	}
	// 与整数 0 应产出相同签名。
	if Sign(map[string]any{"count": negZero}, secret) != Sign(map[string]any{"count": 0}, secret) {
		t.Errorf("-0 与 0 应签名一致")
	}
}

func TestSignIntegerFloatAllowed(t *testing.T) {
	secret := "s"
	// 整数值的浮点（10000.0）应被接受并产出 "10000"。
	base := BuildSignBase(map[string]any{"amount": 10000.0}, secret)
	if !strings.Contains(base, "amount=10000&") {
		t.Errorf("整数浮点应输出 10000，实际 base: %s", base)
	}
}

func TestSignRejectsNonIntegerJSONNumber(t *testing.T) {
	assertSignPanics(t, map[string]any{"amount": json.Number("1.5")}, "非整数 json.Number 应 panic")
	assertSignPanics(t, map[string]any{"amount": json.Number("1e3")}, "指数 json.Number 应 panic")
}

func TestSignRejectsUnknownScalarType(t *testing.T) {
	// complex128 不是容器也不是已知标量 → panic（不再静默签成空串）。
	assertSignPanics(t, map[string]any{"x": complex(1, 2)}, "未知标量类型应 panic")
}

// ===================== [MED1] 具体类型容器签名（reflect） =====================

// TestConcreteMapContainerSigned 校验 map[string]string 不再被签成空串。
func TestConcreteMapContainerSigned(t *testing.T) {
	secret := "s"
	got := BuildSignBase(map[string]any{"extra": map[string]string{"b": "2", "a": "1"}}, secret)
	// 与等价的 map[string]any 应产出完全相同的序列化（key 升序、紧凑）。
	want := BuildSignBase(map[string]any{"extra": map[string]any{"b": "2", "a": "1"}}, secret)
	if got != want {
		t.Errorf("map[string]string 容器序列化分叉\n实际: %s\n期望: %s", got, want)
	}
	if !strings.Contains(got, `extra={"a":"1","b":"2"}`) {
		t.Errorf("map[string]string 未被稳定序列化: %s", got)
	}
}

// TestConcreteSliceContainerSigned 校验 []string 不再被签成空串。
func TestConcreteSliceContainerSigned(t *testing.T) {
	secret := "s"
	got := BuildSignBase(map[string]any{"tags": []string{"b", "a", "c"}}, secret)
	if !strings.Contains(got, `tags=["b","a","c"]`) {
		t.Errorf("[]string 未被稳定序列化: %s", got)
	}
}

// TestConcreteIntSliceContainerSigned 校验 []int 容器。
func TestConcreteIntSliceContainerSigned(t *testing.T) {
	secret := "s"
	got := BuildSignBase(map[string]any{"nums": []int{3, 1, 2}}, secret)
	if !strings.Contains(got, `nums=[3,1,2]`) {
		t.Errorf("[]int 未被稳定序列化: %s", got)
	}
}

// TestStructContainerSigned 校验 struct（按 json tag）容器序列化。
func TestStructContainerSigned(t *testing.T) {
	secret := "s"
	type customer struct {
		LastName  string `json:"last_name"`
		FirstName string `json:"first_name"`
		Skip      string `json:"-"`
		Opt       string `json:"opt,omitempty"`
	}
	got := BuildSignBase(map[string]any{"c": customer{LastName: "Zhang", FirstName: "San", Skip: "x"}}, secret)
	// json:"-" 字段不出现；omitempty 空字段不出现；key 升序。
	if !strings.Contains(got, `c={"first_name":"San","last_name":"Zhang"}`) {
		t.Errorf("struct 容器序列化错误: %s", got)
	}
	if strings.Contains(got, "Skip") || strings.Contains(got, "opt") {
		t.Errorf("json:\"-\"/omitempty 字段不应出现: %s", got)
	}
}

// TestNestedConcreteContainerSigned 校验嵌套层的具体类型容器也被序列化（非空串/null）。
func TestNestedConcreteContainerSigned(t *testing.T) {
	secret := "s"
	got := BuildSignBase(map[string]any{
		"extra": map[string]any{
			"inner": map[string]string{"y": "2", "x": "1"},
		},
	}, secret)
	if !strings.Contains(got, `extra={"inner":{"x":"1","y":"2"}}`) {
		t.Errorf("嵌套具体类型容器序列化错误: %s", got)
	}
}

// ===================== [LOW UTF-8] 非法 UTF-8 fail-closed =====================

func TestSignRejectsInvalidUTF8Nested(t *testing.T) {
	// 孤立的高位字节 0xff 构成非法 UTF-8。
	bad := string([]byte{0x41, 0xff, 0x42})
	assertSignPanics(t, map[string]any{"extra": map[string]any{"note": bad}}, "嵌套非法 UTF-8 应 panic")
}

// ===================== [D] 传输 https 强制 =====================

func TestConfigRejectsInsecureRemoteBaseURL(t *testing.T) {
	c := NewClient(Config{BaseURL: "http://api.example.com/api/open/v1", SecretPay: "s"})
	if c.baseURLErr == nil || !errors.Is(c.baseURLErr, ErrInsecureBaseURL) {
		t.Fatalf("非本地 http 基址应记录 ErrInsecureBaseURL，实际: %v", c.baseURLErr)
	}
	// 首个请求应返回该错误。
	_, err := c.PayCreate(context.Background(), map[string]any{"out_order_no": "x"})
	if err == nil || !errors.Is(err, ErrInsecureBaseURL) {
		t.Fatalf("非本地 http 首个请求应返回 ErrInsecureBaseURL，实际: %v", err)
	}
}

func TestConfigAllowsHTTPSRemote(t *testing.T) {
	c := NewClient(Config{BaseURL: "https://api.example.com/api/open/v1", SecretPay: "s"})
	if c.baseURLErr != nil {
		t.Fatalf("https 远程基址应放行，实际: %v", c.baseURLErr)
	}
}

func TestConfigAllowsLocalHTTP(t *testing.T) {
	for _, base := range []string{
		"http://localhost:3090/api/open/v1",
		"http://127.0.0.1:3090/api/open/v1",
		"http://[::1]:3090/api/open/v1",
	} {
		c := NewClient(Config{BaseURL: base, SecretPay: "s"})
		if c.baseURLErr != nil {
			t.Errorf("本地 http 基址 %q 应放行，实际: %v", base, c.baseURLErr)
		}
	}
}

// ===================== [LOW 重定向] 禁止跟随重定向 =====================

func TestClientDoesNotFollowRedirect(t *testing.T) {
	// 目标服务器：本应被「重定向跟随」打到，这里用于断言「不会」被打到。
	var redirectTargetHit bool
	target := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		redirectTargetHit = true
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(`{"code":0,"message":"ok"}`))
	}))
	defer target.Close()

	// 源服务器：对任何请求都 302 到 target。
	src := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		http.Redirect(w, r, target.URL, http.StatusFound)
	}))
	defer src.Close()

	c := NewClient(Config{BaseURL: src.URL, SecretPay: "s"})
	_, err := c.PayCreate(context.Background(), map[string]any{"out_order_no": "x"})
	// 不跟随重定向 → 收到 302（非 2xx）→ TransportError。
	if err == nil {
		t.Fatalf("收到重定向应作为非 2xx 报 TransportError")
	}
	var tErr *TransportError
	if !errors.As(err, &tErr) || tErr.StatusCode != http.StatusFound {
		t.Fatalf("应为 302 的 TransportError，实际: %v", err)
	}
	if redirectTargetHit {
		t.Fatalf("不应跟随重定向打到目标服务器（POST body 跟随风险）")
	}
}

// ===================== 辅助 =====================

// assertSignPanics 断言对给定 payload 调用 Sign 会 panic。
func assertSignPanics(t *testing.T, payload map[string]any, msg string) {
	t.Helper()
	defer func() {
		if r := recover(); r == nil {
			t.Errorf("%s（未 panic）", msg)
		}
	}()
	_ = Sign(payload, "s")
}
