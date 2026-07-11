package openapi

import (
	"encoding/json"
	"strings"
	"testing"
)

// FuzzVerifyCallbackNeverPanics 验证攻击者可控的 JSON 回调体不会让验签入口向外抛出 panic。
// 回调字段可能新增、类型可能异常；VerifyCallback 应该只返回 false，而不是终止商户进程。
func FuzzVerifyCallbackNeverPanics(f *testing.F) {
	f.Add(`{"merchant_no":"M00000001","amount":10000,"status":"success"}`, "secret", strings.Repeat("0", 64))
	f.Add(`{"nested":{"name":"中文","values":[true,null,3]}}`, "sk_test", strings.Repeat("a", 64))

	f.Fuzz(func(t *testing.T, raw, secret, providedSign string) {
		var payload Payload
		decoder := json.NewDecoder(strings.NewReader(raw))
		decoder.UseNumber()
		if err := decoder.Decode(&payload); err != nil || payload == nil {
			return
		}

		payload["sign"] = providedSign
		_ = VerifyCallback(payload, secret)
	})
}
