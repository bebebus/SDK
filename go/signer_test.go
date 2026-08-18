package openapi

import (
	"bytes"
	"encoding/json"
	"os"
	"path/filepath"
	"testing"
)

// vectorFile 是 test-vectors.json 的结构（只取需要的字段）。
type vectorFile struct {
	Vectors []vector `json:"vectors"`
}

type vector struct {
	Name    string         `json:"name"`
	Desc    string         `json:"desc"`
	Secret  string         `json:"secret"`
	Payload map[string]any `json:"payload"`
	Base    string         `json:"base"`
	Sign    string         `json:"sign"`
}

// loadVectors 读取仓库根的 test-vectors.json（go 子目录的上一级），
// 用 Decoder+UseNumber 解析以保留整数文本形态，避免 float64 把大整数写成 1e+12。
func loadVectors(t *testing.T) []vector {
	t.Helper()
	path := filepath.Join("..", "test-vectors.json")
	raw, err := os.ReadFile(path)
	if err != nil {
		t.Fatalf("读取向量文件失败 %s: %v", path, err)
	}
	dec := json.NewDecoder(bytes.NewReader(raw))
	dec.UseNumber()
	var vf vectorFile
	if err := dec.Decode(&vf); err != nil {
		t.Fatalf("解析向量文件失败: %v", err)
	}
	if len(vf.Vectors) == 0 {
		t.Fatalf("向量文件为空")
	}
	return vf.Vectors
}

// TestVectorsBaseAndSign 对每个向量断言 BuildSignBase==base 且 Sign==sign。
func TestVectorsBaseAndSign(t *testing.T) {
	for _, v := range loadVectors(t) {
		v := v
		t.Run(v.Name, func(t *testing.T) {
			gotBase := BuildSignBase(v.Payload, v.Secret)
			if gotBase != v.Base {
				t.Errorf("base 不匹配\n期望: %s\n实际: %s", v.Base, gotBase)
			}
			gotSign := Sign(v.Payload, v.Secret)
			if gotSign != v.Sign {
				t.Errorf("sign 不匹配\n期望: %s\n实际: %s", v.Sign, gotSign)
			}
		})
	}
}

// TestVerifyCallbackPositive 用向量构造一个带正确 sign 的回调，验签应通过。
func TestVerifyCallbackPositive(t *testing.T) {
	secret := "sk_test_0123456789abcdef0123456789abcdef"
	payload := map[string]any{
		"merchant_no":      "M00000001",
		"order_no":         "P202501010001",
		"out_order_no":     "202501010001",
		"amount":           json.Number("10000"),
		"actual_amount":    json.Number("10000"),
		"currency":         "PHP",
		"status":           "success",
		"channel_order_no": nil, // null 字段不参与签名。
		"paid_at":          "2025-01-01T08:00:00+08:00",
	}
	payload["sign"] = Sign(payload, secret)

	if !VerifyCallback(payload, secret) {
		t.Fatalf("正确签名的回调验签应通过")
	}
}

// TestVerifyCallbackTamperedByte 篡改一个字节后验签应失败（反例）。
func TestVerifyCallbackTamperedByte(t *testing.T) {
	secret := "sk_test_0123456789abcdef0123456789abcdef"
	payload := map[string]any{
		"merchant_no":   "M00000001",
		"payout_no":     "W202501010001",
		"out_payout_no": "WD202501010001",
		"amount":        json.Number("100000"),
		"currency":      "PHP",
		"status":        "success",
		"finished_at":   "2025-01-01T09:00:00+08:00",
	}
	good := Sign(payload, secret)

	// 篡改业务字段一个字节：amount 末位 0→1。
	tampered := cloneParams(payload)
	tampered["amount"] = json.Number("100001")
	tampered["sign"] = good // 沿用旧签名。
	if VerifyCallback(tampered, secret) {
		t.Fatalf("篡改业务字段后验签不应通过")
	}

	// 篡改 sign 本身一个字节。
	bad := flipLastHexByte(good)
	signTampered := cloneParams(payload)
	signTampered["sign"] = bad
	if VerifyCallback(signTampered, secret) {
		t.Fatalf("篡改 sign 后验签不应通过")
	}

	// 缺失 sign 应失败。
	noSign := cloneParams(payload)
	if VerifyCallback(noSign, secret) {
		t.Fatalf("缺失 sign 验签不应通过")
	}
}

// TestVerifyCallbackWrongSecret 用错误密钥验签应失败（代收/代付密钥用错）。
func TestVerifyCallbackWrongSecret(t *testing.T) {
	payCallback := map[string]any{
		"merchant_no": "M00000001",
		"order_no":    "P1",
		"amount":      json.Number("10000"),
		"status":      "success",
	}
	payCallback["sign"] = Sign(payCallback, "secret_pay")

	if VerifyCallback(payCallback, "secret_payout") {
		t.Fatalf("用错误密钥（payout 验代收回调）不应通过")
	}
	if !VerifyCallback(payCallback, "secret_pay") {
		t.Fatalf("用正确密钥应通过")
	}
}

// flipLastHexByte 翻转 hex 串末位字符，制造一字节差异。
func flipLastHexByte(s string) string {
	if s == "" {
		return s
	}
	b := []byte(s)
	last := b[len(b)-1]
	if last == 'f' {
		b[len(b)-1] = 'e'
	} else {
		b[len(b)-1] = last + 1
	}
	return string(b)
}
