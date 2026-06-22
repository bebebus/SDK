package openapi

import (
	"encoding/json"
	"sort"
	"strconv"
	"strings"
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
		sb.WriteString(formatFloat(float64(x)))
	case float64:
		sb.WriteString(formatFloat(x))
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
		// 未知类型兜底为 JSON null，避免 panic。
		sb.WriteString("null")
	}
}

// writeJSONString 写出带双引号、按 JS JSON.stringify 转义的字符串。
//
// 转义：" \ \b \f \n \r \t，其余 U+0000–U+001F 控制字符 → \u00XX（小写四位）。
// 不转义：/ （正斜杠）、< > & （不做 HTML 转义）、所有非 ASCII（原样 UTF-8）。
func writeJSONString(sb *strings.Builder, s string) {
	const hexDigits = "0123456789abcdef"
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

// formatFloat 以与 JS Number→string 接近的最短表示格式化浮点。
// 注意：金额一律用整数，浮点仅作兜底（API 不使用浮点金额）。
func formatFloat(f float64) string {
	return strconv.FormatFloat(f, 'g', -1, 64)
}
