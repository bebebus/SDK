// Package projectp 是 project-p 商户支付 OpenAPI 的零依赖 Go SDK。
//
// signer.go 实现签名算法（HMAC-SHA256 → hex 小写），逐字节对齐服务端
// web-shared/src/openapi/sign.ts。算法规范见仓库根的 SIGNING.md。
package projectp

import (
	"crypto/hmac"
	"crypto/sha256"
	"encoding/hex"
	"sort"
	"strconv"
	"strings"
)

// Payload 是参与签名的键值表（业务字段 + 通用字段，不含 sign）。
// 值类型须为 SDK 可识别的标量或容器：
//   - nil          —— 过滤（不入请求体、不参与签名）
//   - string       —— 顶层原样、嵌套加引号转义
//   - bool         —— true / false
//   - 整数族 int/int8.../uint.../json.Number 的整数形态 —— 十进制文本
//   - map[string]any / []any —— 嵌套，走稳定 JSON 序列化
//
// 金额一律用整数（最小单位，10000 = 1 元），不要用浮点承载金额。
type Payload = map[string]any

// BuildSignBase 构造签名 base 串：
//
//	k1=v1&k2=v2&...&kN=vN&secret=<secret>
//
// 过滤 key=="sign" 与值为 nil 的字段；其余按 key ASCII 升序；顶层标量取原始
// 字符串形态（不加引号），object/array 走稳定 JSON 序列化。便于单测逐字节断言。
func BuildSignBase(payload Payload, secret string) string {
	keys := make([]string, 0, len(payload))
	for k, v := range payload {
		if k == "sign" {
			continue
		}
		if v == nil {
			continue
		}
		keys = append(keys, k)
	}
	sort.Strings(keys)

	var b strings.Builder
	for _, k := range keys {
		b.WriteString(k)
		b.WriteByte('=')
		b.WriteString(valueForSign(payload[k]))
		b.WriteByte('&')
	}
	b.WriteString("secret=")
	b.WriteString(secret)
	return b.String()
}

// Sign 计算请求/回调签名：HMAC-SHA256(BuildSignBase, key=secret)，hex 小写。
func Sign(payload Payload, secret string) string {
	base := BuildSignBase(payload, secret)
	mac := hmac.New(sha256.New, []byte(secret))
	mac.Write([]byte(base))
	return hex.EncodeToString(mac.Sum(nil))
}

// VerifyCallback 校验回调签名（时序安全比较，字段无关）。
//
// 规则：取回调表里除 sign 外的所有字段参与签名，与回调携带的 sign 比对。
// 不硬编码字段清单——平台可能增删字段，只依赖「除 sign 外全部参与」这一规则。
// 代收/退款回调用 api_secret_pay，代付回调用 api_secret_payout。
func VerifyCallback(payload Payload, secret string) bool {
	provided, ok := payload["sign"].(string)
	if !ok || provided == "" {
		return false
	}
	expected := Sign(payload, secret)
	// 时序安全比较，避免按字节短路泄漏。
	return hmac.Equal([]byte(expected), []byte(provided))
}

// valueForSign 取顶层字段的签名用字符串：标量原样（不加引号），容器走稳定 JSON。
func valueForSign(v any) string {
	switch x := v.(type) {
	case nil:
		return "" // 调用方已过滤 nil，这里仅兜底。
	case string:
		return x // 顶层字符串原样：不转义、不加引号、不 URL 编码。
	case bool:
		return strconv.FormatBool(x) // true / false（不可用语言默认 cast）。
	case map[string]any, []any:
		var sb strings.Builder
		stableStringify(&sb, x)
		return sb.String()
	default:
		// 数字（整数族、json.Number）与其余标量。
		return scalarToString(v)
	}
}

// scalarToString 把标量归一为与 JS String(v) 一致的文本（无引号）。
func scalarToString(v any) string {
	switch x := v.(type) {
	case string:
		return x
	case bool:
		return strconv.FormatBool(x)
	case int:
		return strconv.FormatInt(int64(x), 10)
	case int8:
		return strconv.FormatInt(int64(x), 10)
	case int16:
		return strconv.FormatInt(int64(x), 10)
	case int32:
		return strconv.FormatInt(int64(x), 10)
	case int64:
		return strconv.FormatInt(x, 10)
	case uint:
		return strconv.FormatUint(uint64(x), 10)
	case uint8:
		return strconv.FormatUint(uint64(x), 10)
	case uint16:
		return strconv.FormatUint(uint64(x), 10)
	case uint32:
		return strconv.FormatUint(uint64(x), 10)
	case uint64:
		return strconv.FormatUint(x, 10)
	case jsonNumber:
		// json.Number：保留原始文本形态（避免 float64 把大整数写成 1e+12）。
		return string(x)
	case float32:
		return formatFloat(float64(x))
	case float64:
		return formatFloat(x)
	default:
		// 不应到达；为可控起见返回空串而非 panic。
		return ""
	}
}
