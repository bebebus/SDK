package openapi

import (
	"crypto/rand"
	"encoding/hex"
	"errors"
	"fmt"
	"net/url"
	"strings"
	"time"
)

// Environment 是预设环境，决定默认 Base URL。
type Environment int

const (
	// Production 正式环境。无内置 URL：正式基址请向服务商获取，
	// 形如 https://api.<domain>/api/open/v1，必须用 Config.BaseURL 显式提供。
	Production Environment = iota
	// Sandbox 本地/联调环境。
	Sandbox
)

const (
	sandboxBaseURL = "http://127.0.0.1:3090/api/open/v1"
)

// ErrBaseURLRequired 表示选用 Production 但未提供 BaseURL。
var ErrBaseURLRequired = errors.New("baseUrl is required: obtain the production URL from your service provider (e.g. https://api.<domain>/api/open/v1)")

// ErrInsecureBaseURL 表示非本地的 baseUrl 用了非 https（明文传输密钥/签名，拒绝）。
var ErrInsecureBaseURL = errors.New("baseUrl must use https:// for non-local hosts (refuse to transmit credentials over plaintext)")

// BaseURL 返回环境对应的预设基址。Production 无内置基址，返回空串。
func (e Environment) BaseURL() string {
	switch e {
	case Sandbox:
		return sandboxBaseURL
	default:
		return ""
	}
}

// Config 是 Client 的构造参数。
type Config struct {
	// MerchantNo 商户号（必填）。
	MerchantNo string
	// APIKey API Key（必填）。
	APIKey string
	// SecretPay 代收密钥 api_secret_pay：pay/*、pay-methods/query、balance/query 及代收/退款回调使用。
	SecretPay string
	// SecretPayout 代付密钥 api_secret_payout：payout/* 及代付回调使用。
	SecretPayout string

	// Environment 预设环境，默认 Production。BaseURL 非空时优先于本字段。
	Environment Environment
	// BaseURL 自定义基址覆盖（服务商提供的地址或本地端口），非空则覆盖 Environment 预设。
	BaseURL string

	// Timeout HTTP 请求超时，默认 30s。
	Timeout time.Duration
}

// resolveBaseURL 决定最终基址：BaseURL 非空优先，否则取 Environment 预设。
// 最终基址为空（选了 Production 又未传 BaseURL）时返回 ErrBaseURLRequired。
//
// [D] 传输安全：自定义 BaseURL 若指向非本地主机（非 localhost/127.0.0.1/[::1]）却用
// 非 https，明文承载密钥/签名，直接拒绝（ErrInsecureBaseURL）。本地联调放行 http。
func (c Config) resolveBaseURL() (string, error) {
	if c.BaseURL != "" {
		if err := validateTransportSecurity(c.BaseURL); err != nil {
			return "", err
		}
		return c.BaseURL, nil
	}
	base := c.Environment.BaseURL()
	if base == "" {
		return "", ErrBaseURLRequired
	}
	return base, nil
}

// validateTransportSecurity 校验 baseUrl 的传输安全：非本地主机必须 https。
// https 一律放行；http 仅当主机是 localhost/127.0.0.1/[::1] 时放行（本地联调）。
func validateTransportSecurity(raw string) error {
	u, err := url.Parse(raw)
	if err != nil {
		return fmt.Errorf("%w: 无法解析 baseUrl %q: %v", ErrInsecureBaseURL, raw, err)
	}
	scheme := strings.ToLower(u.Scheme)
	if scheme == "https" {
		return nil
	}
	if scheme == "http" && isLocalHost(u.Hostname()) {
		return nil // 本地联调放行 http。
	}
	return fmt.Errorf("%w: %q", ErrInsecureBaseURL, raw)
}

// isLocalHost 判定主机名是否为本地环回地址（放行 http 的唯一例外）。
func isLocalHost(host string) bool {
	switch strings.ToLower(host) {
	case "localhost", "127.0.0.1", "::1":
		return true
	default:
		return false
	}
}

// resolveTimeout 决定最终超时：Timeout > 0 优先，否则 30s 默认。
func (c Config) resolveTimeout() time.Duration {
	if c.Timeout > 0 {
		return c.Timeout
	}
	return 30 * time.Second
}

// nowUnix 返回当前 Unix 秒（可在测试中替换为固定时钟）。
var nowUnix = func() int64 { return time.Now().Unix() }

// newNonce 生成每请求唯一的防重放随机串（16 字节 → 32 位 hex）。
var newNonce = func() string {
	buf := make([]byte, 16)
	if _, err := rand.Read(buf); err != nil {
		// [L21] 支付场景宁失败不弱化随机性：crypto/rand 失败即 panic，
		// 绝不退化为可预测的时间戳串（防重放 nonce 一旦可预测即失去意义）。
		panic("openapi: 生成 nonce 失败——加密随机源不可用，拒绝用可预测值兜底: " + err.Error())
	}
	return hex.EncodeToString(buf)
}
