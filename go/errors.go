package openapi

import "fmt"

// APIError 表示业务失败（统一信封 code != 0）。携带原始 code/message/data 供调用方判断，
// 不要穷举写死分支——错误码以服务端为准，可能新增。
type APIError struct {
	Code    int            // 服务端业务码（见 INTERFACES.md §六）。
	Message string         // 服务端 message（如具体字段校验错误）。
	Data    map[string]any // 失败时可能附带的 data（如 missing_fields）。
	Raw     []byte         // 原始响应体，供进一步排查。
}

func (e *APIError) Error() string {
	return fmt.Sprintf("openapi api error: code=%d message=%s", e.Code, e.Message)
}

// TransportError 表示 HTTP/网络/解析层错误（非业务失败）：连接失败、超时、非 JSON 响应、
// 非 2xx 状态等。Raw 在可得时保留原始响应体。
type TransportError struct {
	Op         string // 出错环节，如 "request"、"decode"。
	StatusCode int    // HTTP 状态码（0 表示请求未发出或无响应）。
	Raw        []byte // 原始响应体（可空）。
	Err        error  // 底层错误。
}

func (e *TransportError) Error() string {
	if e.StatusCode != 0 {
		return fmt.Sprintf("openapi transport error: op=%s status=%d: %v", e.Op, e.StatusCode, e.Err)
	}
	return fmt.Sprintf("openapi transport error: op=%s: %v", e.Op, e.Err)
}

func (e *TransportError) Unwrap() error { return e.Err }
