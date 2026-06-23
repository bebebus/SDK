// Package openapi 是商户支付 OpenAPI 的零依赖 Go SDK。
//
// signer.go 实现签名算法（HMAC-SHA256 → hex 小写），与服务端签名实现逐字节一致。
// 算法规范见仓库根的 SIGNING.md。
package openapi

import (
	"crypto/hmac"
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"math"
	"reflect"
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
//
// fail-closed：secret 为空串/全空白时 panic（禁止用空密钥签名/验签，从根上堵掉
// 空密钥旁路）。参与签名的数值若为 NaN/Infinity/非整数浮点或无法识别的类型，
// 同样 panic（合约要求金额用整数最小单位）。验签路径 VerifyCallback 会 recover
// 这些 panic 并归一为 false，绝不向调用方冒泡。
func Sign(payload Payload, secret string) string {
	if isBlankSecret(secret) {
		panic("openapi: secret 不可为空（拒绝用空密钥签名）")
	}
	base := BuildSignBase(payload, secret)
	mac := hmac.New(sha256.New, []byte(secret))
	mac.Write([]byte(base))
	return hex.EncodeToString(mac.Sum(nil))
}

// isBlankSecret 判定密钥为空串或全空白（fail-closed 的统一口径）。
func isBlankSecret(secret string) bool {
	return strings.TrimSpace(secret) == ""
}

// VerifyCallback 校验回调签名（时序安全比较，字段无关）。
//
// 规则：取回调表里除 sign 外的所有字段参与签名，与回调携带的 sign 比对。
// 不硬编码字段清单——平台可能增删字段，只依赖「除 sign 外全部参与」这一规则。
// 代收/退款回调用 api_secret_pay，代付回调用 api_secret_payout。
func VerifyCallback(payload Payload, secret string) (ok bool) {
	// [A] fail-closed：空/全空白密钥在算任何 HMAC 之前直接拒绝，绝不继续比对。
	if isBlankSecret(secret) {
		return false
	}
	// [B] 回调体为 nil（非对象）：攻击者可控的异常输入，直接拒绝。
	if payload == nil {
		return false
	}
	// [B] 提供的 sign 必须是合法十六进制字符串、长度恰为 64（HMAC-SHA256 hex）。
	provided, isStr := payload["sign"].(string)
	if !isStr || !isValidHexSign(provided) {
		return false
	}
	// [B] Sign 内部对空密钥/非法数值/未知类型会 panic；验签路径一律 recover 为 false，
	// 绝不让异常向调用方冒泡。
	defer func() {
		if r := recover(); r != nil {
			ok = false
		}
	}()
	expected := Sign(payload, secret)
	// 时序安全比较，避免按字节短路泄漏。
	return hmac.Equal([]byte(expected), []byte(provided))
}

// isValidHexSign 判定签名串是否为合法的 HMAC-SHA256 hex（64 位小写/大写十六进制）。
// 长度异常或含非十六进制字符（攻击者可控）一律判非法。
func isValidHexSign(sign string) bool {
	if len(sign) != sha256.Size*2 { // 32 字节 → 64 个 hex 字符。
		return false
	}
	for i := 0; i < len(sign); i++ {
		c := sign[i]
		if !((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')) {
			return false
		}
	}
	return true
}

// valueForSign 取顶层字段的签名用字符串：标量原样（不加引号），容器走稳定 JSON。
//
// [MED1] 容器签名修复：不再只认 map[string]any/[]any，凡是 Map/Slice/Array/Struct
// 等具体容器类型（如 map[string]string、[]string、自定义 struct）都用 reflect 归一
// 为 map[string]any/[]any 再走 stableStringify，杜绝「具体类型容器被静默签成空串」
// 的签名豁免与对接断裂。无法识别的类型由 scalarToString 抛 panic（验签路径会 recover）。
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
		// 先判是否为反射意义上的容器（Map/Slice/Array/Struct，排除 []byte 等已知标量场景）。
		if normalized, isContainer := normalizeContainer(v); isContainer {
			var sb strings.Builder
			stableStringify(&sb, normalized)
			return sb.String()
		}
		// 数字（整数族、json.Number）与其余标量。
		return scalarToString(v)
	}
}

// normalizeContainer 用反射把具体类型的容器（map / slice / array / struct）归一为
// stableStringify 能稳定序列化的 map[string]any / []any。返回的第二值标记 v 是否为容器。
//
//   - Map：键统一转字符串（与 JSON 对象一致，key 在 stableStringify 内再升序）。
//   - Slice/Array：逐元素归一为 []any（保持顺序）。空切片/数组 → []any{}（序列化为 []）。
//   - Struct：按 json tag / 字段名（导出字段）映射为对象，遵循 omitempty 与 `json:"-"`。
//   - 指针：解引用后递归（nil 指针视为 null）。
//
// 非容器（标量、json.Number、字符串等）返回 (nil, false)，交回标量分支处理。
func normalizeContainer(v any) (any, bool) {
	rv := reflect.ValueOf(v)
	switch rv.Kind() {
	case reflect.Map:
		out := make(map[string]any, rv.Len())
		for _, mk := range rv.MapKeys() {
			out[mapKeyToString(mk)] = reflectToSignable(rv.MapIndex(mk))
		}
		return out, true
	case reflect.Slice, reflect.Array:
		// []byte 等字节切片不当作字符数组签名：交回标量分支（当前无对应标量处理则报错）。
		if rv.Kind() == reflect.Slice && rv.Type().Elem().Kind() == reflect.Uint8 {
			return nil, false
		}
		out := make([]any, rv.Len())
		for i := 0; i < rv.Len(); i++ {
			out[i] = reflectToSignable(rv.Index(i))
		}
		return out, true
	case reflect.Struct:
		return structToMap(rv), true
	case reflect.Ptr:
		if rv.IsNil() {
			return nil, true // nil 指针 → null（stableStringify 写 "null"）。
		}
		return normalizeContainer(rv.Elem().Interface())
	default:
		return nil, false
	}
}

// reflectToSignable 把反射值递归归一为 stableStringify 可识别的形态：
// 容器走 normalizeContainer，标量保持其底层 any 值。
func reflectToSignable(rv reflect.Value) any {
	if !rv.IsValid() {
		return nil
	}
	// 解开 interface 包装，拿到真实底层值。
	for rv.Kind() == reflect.Interface {
		if rv.IsNil() {
			return nil
		}
		rv = rv.Elem()
	}
	if rv.Kind() == reflect.Ptr {
		if rv.IsNil() {
			return nil
		}
		rv = rv.Elem()
	}
	val := rv.Interface()
	if normalized, isContainer := normalizeContainer(val); isContainer {
		return normalized
	}
	return val
}

// mapKeyToString 把 map 键统一转为字符串（与 JSON 对象键一致）。
func mapKeyToString(k reflect.Value) string {
	for k.Kind() == reflect.Interface || k.Kind() == reflect.Ptr {
		if k.IsNil() {
			return ""
		}
		k = k.Elem()
	}
	if k.Kind() == reflect.String {
		return k.String()
	}
	return scalarToString(k.Interface())
}

// structToMap 把 struct 按 json tag 规则映射为 map[string]any（仅导出字段）。
// 支持 `json:"name"`、`json:",omitempty"`、`json:"-"`（忽略字段）。
func structToMap(rv reflect.Value) map[string]any {
	t := rv.Type()
	out := make(map[string]any, t.NumField())
	for i := 0; i < t.NumField(); i++ {
		f := t.Field(i)
		if f.PkgPath != "" {
			continue // 非导出字段跳过。
		}
		name := f.Name
		omitempty := false
		if tag, has := f.Tag.Lookup("json"); has {
			parts := strings.Split(tag, ",")
			if parts[0] == "-" {
				continue // json:"-" 显式忽略。
			}
			if parts[0] != "" {
				name = parts[0]
			}
			for _, opt := range parts[1:] {
				if opt == "omitempty" {
					omitempty = true
				}
			}
		}
		fv := rv.Field(i)
		if omitempty && fv.IsZero() {
			continue
		}
		out[name] = reflectToSignable(fv)
	}
	return out
}

// scalarToString 把标量归一为与 JS String(v) 一致的文本（无引号）。
//
// [C] 数值规范：NaN/Infinity 一律拒绝；非整数浮点拒绝（合约要求金额用整数最小单位）；
// -0 归一为 "0"。json.Number 若非整数文本同样拒绝。无法识别的类型 panic（不再静默
// 返回空串，杜绝签名豁免）。这些 panic 在验签路径会被 VerifyCallback recover 为 false。
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
		// json.Number：保留原始文本形态（避免 float64 把大整数写成 1e+12）；
		// 但若是非整数（含小数点/指数）或非法数字，按合约拒绝。
		return jsonNumberToString(x)
	case float32:
		return integerFloatToString(float64(x))
	case float64:
		return integerFloatToString(x)
	default:
		// 无法识别的类型：fail-fast，不再静默返回空串造成签名豁免。
		panic(fmt.Sprintf("openapi: 无法签名的字段类型 %T（仅支持标量、json.Number 与容器）", v))
	}
}

// integerFloatToString 校验并格式化浮点数：拒绝 NaN/Infinity 与非整数浮点，-0 归一 "0"。
func integerFloatToString(f float64) string {
	if math.IsNaN(f) || math.IsInf(f, 0) {
		panic("openapi: 数值不可为 NaN/Infinity（金额请用整数最小单位）")
	}
	if f != math.Trunc(f) {
		panic(fmt.Sprintf("openapi: 数值 %v 非整数（金额请用整数最小单位，勿用浮点承载金额）", f))
	}
	if f == 0 {
		return "0" // 含 -0：归一为 "0"。
	}
	// 此处 f 已确认为整数值：用 'f' 且精度 0 输出十进制整数文本（不带小数点/指数）。
	return strconv.FormatFloat(f, 'f', -1, 64)
}

// jsonNumberToString 校验 json.Number 为整数文本后返回；非整数/非法一律拒绝。
func jsonNumberToString(n jsonNumber) string {
	s := string(n)
	if _, err := n.Int64(); err == nil {
		return s // 标准 int64 范围内的整数文本：原样返回。
	}
	// 超出 int64 但仍为纯整数文本（如超大金额）也放行；含小数点/指数/非法则拒绝。
	if isIntegerNumericText(s) {
		return s
	}
	panic(fmt.Sprintf("openapi: 数值 %q 非整数（金额请用整数最小单位）", s))
}

// isIntegerNumericText 判定字符串是否为纯十进制整数文本（可带前导负号，无小数点/指数）。
func isIntegerNumericText(s string) bool {
	if s == "" {
		return false
	}
	i := 0
	if s[0] == '-' || s[0] == '+' {
		i = 1
	}
	if i >= len(s) {
		return false
	}
	for ; i < len(s); i++ {
		if s[i] < '0' || s[i] > '9' {
			return false
		}
	}
	return true
}
