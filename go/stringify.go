package openapi

import (
	"encoding/json"
	"fmt"
	"sort"
	"strconv"
	"strings"
	"unicode/utf8"
)

// jsonNumber 别名，便于 signer.go 内类型分派时引用。
type jsonNumber = json.Number

// stableStringify 把嵌套 object/array 递归序列化为与 JS JSON.stringify + key 升序
// 完全一致的紧凑字符串（无空格）。规则见 SIGNING.md §三：
//   - null            → null
//   - string          → 带双引号并按 JS 转义（不转 / 非ASCII <>&，转 " \ 控制字符）
//   - number          → 字面量（整数保留原始文本形态，浮点用最短表示）
//   - bool            → true / false
//   - array           → [e0,e1,...]（保持元素顺序）
//   - object          → {"k0":v0,...}（key 升序）
func stableStringify(sb *strings.Builder, v any) {
	switch x := v.(type) {
	case nil:
		sb.WriteString("null")
	case string:
		writeJSONString(sb, x)
	case bool:
		sb.WriteString(strconv.FormatBool(x))
	case json.Number:
		// 原始文本形态：整数不会被转成科学计数法。
		sb.WriteString(string(x))
	case int, int8, int16, int32, int64,
		uint, uint8, uint16, uint32, uint64:
		sb.WriteString(scalarToString(x))
	case float32:
		// [C] 嵌套浮点同样走整数校验：拒绝 NaN/Infinity/非整数，-0→"0"。
		sb.WriteString(integerFloatToString(float64(x)))
	case float64:
		sb.WriteString(integerFloatToString(x))
	case []any:
		sb.WriteByte('[')
		for i, e := range x {
			if i > 0 {
				sb.WriteByte(',')
			}
			stableStringify(sb, e)
		}
		sb.WriteByte(']')
	case map[string]any:
		keys := make([]string, 0, len(x))
		for k := range x {
			keys = append(keys, k)
		}
		sort.Strings(keys)
		sb.WriteByte('{')
		for i, k := range keys {
			if i > 0 {
				sb.WriteByte(',')
			}
			writeJSONString(sb, k)
			sb.WriteByte(':')
			stableStringify(sb, x[k])
		}
		sb.WriteByte('}')
	default:
		// [MED1] 嵌套层的具体类型容器（map[string]string、[]string、struct、指针等）
		// 同样用反射归一后递归序列化，避免被静默签成 null。
		if normalized, isContainer := normalizeContainer(v); isContainer {
			stableStringify(sb, normalized)
			return
		}
		// 仍无法识别：fail-fast，不再静默写 "null" 造成签名豁免。
		panic(fmt.Sprintf("openapi: 无法签名的嵌套字段类型 %T", v))
	}
}

// writeJSONString 写出带双引号、按 JS JSON.stringify 转义的字符串。
//
// 转义：" \ \b \f \n \r \t，其余 U+0000–U+001F 控制字符 → \u00XX（小写四位）。
// 不转义：/ （正斜杠）、< > & （不做 HTML 转义）、所有非 ASCII（原样 UTF-8）。
//
// [LOW UTF-8] fail-closed：先校验 UTF-8 合法性。非法字节序列或孤立代理会让 Go 的
// range 产出 U+FFFD 替换符，与 JS（要求 well-formed）转义结果分叉；为避免跨语言签名
// 静默分叉，遇非法 UTF-8 直接 panic（验签路径会 recover 为 false）。
func writeJSONString(sb *strings.Builder, s string) {
	const hexDigits = "0123456789abcdef"
	if !utf8.ValidString(s) {
		panic("openapi: 待签名字符串含非法 UTF-8（拒绝以免跨语言签名分叉）")
	}
	sb.WriteByte('"')
	for _, r := range s {
		switch r {
		case '"':
			sb.WriteString(`\"`)
		case '\\':
			sb.WriteString(`\\`)
		case '\b':
			sb.WriteString(`\b`)
		case '\f':
			sb.WriteString(`\f`)
		case '\n':
			sb.WriteString(`\n`)
		case '\r':
			sb.WriteString(`\r`)
		case '\t':
			sb.WriteString(`\t`)
		default:
			if r < 0x20 {
				// 其余控制字符 → \u00XX。
				sb.WriteString(`\u00`)
				sb.WriteByte(hexDigits[(r>>4)&0xf])
				sb.WriteByte(hexDigits[r&0xf])
			} else {
				// 含非 ASCII：原样 UTF-8 输出（WriteRune 编码）。
				sb.WriteRune(r)
			}
		}
	}
	sb.WriteByte('"')
}
