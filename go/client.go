package openapi

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"strings"
)

// Version 是 SDK 版本号单一事实源：UA 由它派生（不再硬编码）。
// release.sh 发版打 tag go/vX.Y.Z 时，同步 sed 此常量保持一致。
const Version = "1.1.2"

// secretKind 标记某端点该用哪把密钥。
type secretKind int

const (
	usePay    secretKind = iota // api_secret_pay
	usePayout                   // api_secret_payout
)

// Response 是一次调用的完整结果，便于调用方在不抛异常时直接读取。
type Response struct {
	Code    int            // 业务码，0 为成功。
	Message string         // 服务端 message。
	Data    map[string]any // 业务数据（可空）。
	Raw     []byte         // 原始响应体。
}

// Client 是商户 OpenAPI 客户端。零值不可用，请用 NewClient 构造。
type Client struct {
	cfg        Config
	baseURL    string
	baseURLErr error // 基址解析错误（如选 Production 未传 BaseURL），首个请求时返回。
	httpClient *http.Client
}

// NewClient 用给定配置构造客户端。BaseURL 非空时覆盖 Environment 预设。
//
// 注意：Production 没有内置 URL。若选用 Production 又未提供 BaseURL，构造不会
// panic，但首个请求会返回 ErrBaseURLRequired（正式基址请向服务商获取，
// 形如 https://api.<service_domain>/api/open/v1）。
func NewClient(cfg Config) *Client {
	base, err := cfg.resolveBaseURL()
	return &Client{
		cfg:        cfg,
		baseURL:    strings.TrimRight(base, "/"),
		baseURLErr: err,
		httpClient: &http.Client{
			Timeout: cfg.resolveTimeout(),
			// [LOW 重定向] OpenAPI 是固定 POST 端点，任何重定向都不合法（可能把 POST body
			// 跟随到非预期主机或被降级）。禁止自动跟随：返回最后一次响应交由上层判状态码。
			CheckRedirect: func(req *http.Request, via []*http.Request) error {
				return http.ErrUseLastResponse
			},
		},
	}
}

// ===================== 代收（Pay，密钥 api_secret_pay） =====================

// PayCreate 代收下单。params 为业务字段（out_order_no/amount/currency/pay_method/notify_url 等），
// 通用字段与签名由 SDK 自动注入。值为 nil 的字段不入请求体也不参与签名。
func (c *Client) PayCreate(ctx context.Context, params map[string]any) (*Response, error) {
	return c.call(ctx, "/merchant/pay/create", params, usePay)
}

// PayQuery 代收查单。params 须含 order_no 或 out_order_no（二选一）。
func (c *Client) PayQuery(ctx context.Context, params map[string]any) (*Response, error) {
	return c.call(ctx, "/merchant/pay/query", params, usePay)
}

// PayMethodsQuery 可用支付方式。params 可含 country 过滤（可空）。
func (c *Client) PayMethodsQuery(ctx context.Context, params map[string]any) (*Response, error) {
	return c.call(ctx, "/merchant/pay-methods/query", params, usePay)
}

// BalanceQuery 余额查询。params 可含 currency 过滤（可空）。
func (c *Client) BalanceQuery(ctx context.Context, params map[string]any) (*Response, error) {
	return c.call(ctx, "/merchant/balance/query", params, usePay)
}

// PayTestComplete 代收测试单完成（仅测试密钥）。params 须含 order_no/out_order_no 之一
// 与 result(success|failed)，可选 actual_amount。
func (c *Client) PayTestComplete(ctx context.Context, params map[string]any) (*Response, error) {
	return c.call(ctx, "/merchant/pay/test/complete", params, usePay)
}

// ===================== 代付（Payout，密钥 api_secret_payout） =====================

// PayoutCreate 代付下单。params 为业务字段（out_payout_no/amount/currency/pay_method/
// notify_url/account_no 等，银行类必填 bank_code）。
func (c *Client) PayoutCreate(ctx context.Context, params map[string]any) (*Response, error) {
	return c.call(ctx, "/merchant/payout/create", params, usePayout)
}

// PayoutQuery 代付查单。params 须含 payout_no 或 out_payout_no（二选一）。
func (c *Client) PayoutQuery(ctx context.Context, params map[string]any) (*Response, error) {
	return c.call(ctx, "/merchant/payout/query", params, usePayout)
}

// PayoutBanksQuery 可用银行。params 须含 pay_method/country/currency。
// pay_method 表示支付能力（查询银行时通常固定为 bank），不是 payout/create 的支付分组。
func (c *Client) PayoutBanksQuery(ctx context.Context, params map[string]any) (*Response, error) {
	return c.call(ctx, "/merchant/payout/banks/query", params, usePayout)
}

// PayoutProofQuery 代付凭证查询（仅 status=success 可查）。params 须含 payout_no 或 out_payout_no。
func (c *Client) PayoutProofQuery(ctx context.Context, params map[string]any) (*Response, error) {
	return c.call(ctx, "/merchant/payout/proof/query", params, usePayout)
}

// PayoutReceiptQuery 代付收据。params 须含 payout_no 或 out_payout_no，可选 lang。
//
// inline 单独传参并以整数 1/0 发送（避免布尔跨语言签名歧义）：
// inline=true → 内联 base64 图片；inline=false → 带 token 的 URL。
func (c *Client) PayoutReceiptQuery(ctx context.Context, params map[string]any, inline bool) (*Response, error) {
	merged := cloneParams(params)
	if inline {
		merged["inline"] = 1
	} else {
		merged["inline"] = 0
	}
	return c.call(ctx, "/merchant/payout/receipt/query", merged, usePayout)
}

// PayoutTestComplete 代付测试单完成（仅测试密钥）。params 须含 payout_no/out_payout_no 之一
// 与 result(success|failed)。
func (c *Client) PayoutTestComplete(ctx context.Context, params map[string]any) (*Response, error) {
	return c.call(ctx, "/merchant/payout/test/complete", params, usePayout)
}

// ===================== 回调验签 =====================

// VerifyPayCallback 校验代收/退款回调（密钥 api_secret_pay，时序安全）。
func (c *Client) VerifyPayCallback(payload map[string]any) bool {
	return VerifyCallback(payload, c.cfg.SecretPay)
}

// VerifyPayoutCallback 校验代付回调（密钥 api_secret_payout，时序安全）。
func (c *Client) VerifyPayoutCallback(payload map[string]any) bool {
	return VerifyCallback(payload, c.cfg.SecretPayout)
}

// ===================== 内部：请求构建 / 发送 / 解析 =====================

// secretFor 返回端点对应的密钥。
func (c *Client) secretFor(kind secretKind) string {
	if kind == usePayout {
		return c.cfg.SecretPayout
	}
	return c.cfg.SecretPay
}

// call 构建请求体（注入通用字段 + nonce + timestamp + sign），POST 并解析统一信封。
func (c *Client) call(ctx context.Context, path string, params map[string]any, kind secretKind) (*Response, error) {
	// 基址解析失败（如选 Production 未传 BaseURL）：首个请求即清晰报错。
	if c.baseURLErr != nil {
		return nil, c.baseURLErr
	}
	secret := c.secretFor(kind)
	body := c.buildBody(params, secret)

	raw, status, err := c.do(ctx, path, body)
	if err != nil {
		return nil, err
	}

	// 解析统一信封；用 Decoder+UseNumber 避免大整数被 float64 写成科学计数法。
	var envelope struct {
		Code    int             `json:"code"`
		Message string          `json:"message"`
		Data    json.RawMessage `json:"data"`
	}
	dec := json.NewDecoder(bytes.NewReader(raw))
	dec.UseNumber()
	if err := dec.Decode(&envelope); err != nil {
		return nil, &TransportError{Op: "decode", StatusCode: status, Raw: raw, Err: err}
	}

	data := decodeData(envelope.Data)
	resp := &Response{Code: envelope.Code, Message: envelope.Message, Data: data, Raw: raw}

	if envelope.Code != 0 {
		return resp, &APIError{Code: envelope.Code, Message: envelope.Message, Data: data, Raw: raw}
	}
	return resp, nil
}

// buildBody 注入通用字段并签名，返回最终请求体（含 sign）。
// 过滤值为 nil 的字段（既不入体、也不参与签名）。
func (c *Client) buildBody(params map[string]any, secret string) map[string]any {
	body := make(map[string]any, len(params)+5)
	for k, v := range params {
		if v == nil {
			continue // null/nil 字段不放入请求体也不参与签名。
		}
		body[k] = v
	}
	body["merchant_no"] = c.cfg.MerchantNo
	body["api_key"] = c.cfg.APIKey
	body["timestamp"] = nowUnix()
	body["nonce"] = newNonce()

	body["sign"] = Sign(body, secret)
	return body
}

// do 执行 POST application/json，返回原始响应体与状态码。
func (c *Client) do(ctx context.Context, path string, body map[string]any) ([]byte, int, error) {
	payload, err := marshalNoEscape(body)
	if err != nil {
		return nil, 0, &TransportError{Op: "marshal", Err: err}
	}

	url := c.baseURL + path
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, url, bytes.NewReader(payload))
	if err != nil {
		return nil, 0, &TransportError{Op: "request", Err: err}
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Accept", "application/json")
	// 自识别 User-Agent：避免被 WAF/CDN（如 Cloudflare）按默认 UA 拦成 403。
	// 版本号从 Version 常量单一派生。
	req.Header.Set("User-Agent", "openapi-sdk-go/"+Version)

	res, err := c.httpClient.Do(req)
	if err != nil {
		return nil, 0, &TransportError{Op: "request", Err: err}
	}
	defer func() {
		_ = res.Body.Close()
	}()

	raw, err := io.ReadAll(res.Body)
	if err != nil {
		return nil, res.StatusCode, &TransportError{Op: "read", StatusCode: res.StatusCode, Err: err}
	}

	if res.StatusCode < 200 || res.StatusCode >= 300 {
		return raw, res.StatusCode, &TransportError{
			Op:         "http",
			StatusCode: res.StatusCode,
			Raw:        raw,
			Err:        fmt.Errorf("unexpected http status %d", res.StatusCode),
		}
	}
	return raw, res.StatusCode, nil
}

// marshalNoEscape 用 Encoder.SetEscapeHTML(false) 序列化请求体，与签名口径一致
// （不把 <>& 转义）。注意：此处仅为发送线缆字节，签名仍由 Sign 自行构造 base。
func marshalNoEscape(v any) ([]byte, error) {
	var buf bytes.Buffer
	enc := json.NewEncoder(&buf)
	enc.SetEscapeHTML(false)
	if err := enc.Encode(v); err != nil {
		return nil, err
	}
	// Encoder.Encode 会追加换行，去掉以保持纯净 JSON。
	return bytes.TrimRight(buf.Bytes(), "\n"), nil
}

// decodeData 把 data 段解析为 map[string]any（UseNumber 保留整数文本形态）。
// data 为 null、空或非对象时返回 nil。
func decodeData(raw json.RawMessage) map[string]any {
	if len(raw) == 0 || string(raw) == "null" {
		return nil
	}
	var m map[string]any
	dec := json.NewDecoder(bytes.NewReader(raw))
	dec.UseNumber()
	if err := dec.Decode(&m); err != nil {
		return nil // data 不是对象（如数组场景）时调用方可自行从 Raw 解析。
	}
	return m
}

// cloneParams 浅拷贝入参，避免修改调用方传入的 map。
func cloneParams(params map[string]any) map[string]any {
	out := make(map[string]any, len(params)+1)
	for k, v := range params {
		out[k] = v
	}
	return out
}
