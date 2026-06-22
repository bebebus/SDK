package projectp

import (
	"crypto/rand"
	"encoding/hex"
	"time"
)

// Environment 是预设环境，决定默认 Base URL。
type Environment int

const (
	// Production 正式环境（文档默认地址；真实正式地址按上级代理专有域名派生，
	// 形如 https://api.<agent_domain>/api/open/v1，用 Config.BaseURL 覆盖）。
	Production Environment = iota
	// Sandbox 本地/联调环境。
	Sandbox
)

const (
	productionBaseURL = "https://api.project-p-merchant.cniia.cloud/api/open/v1"
	sandboxBaseURL    = "http://127.0.0.1:3090/api/open/v1"
)

// BaseURL 返回环境对应的预设基址。
func (e Environment) BaseURL() string {
	switch e {
	case Sandbox:
		return sandboxBaseURL
	default:
		return productionBaseURL
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
	// BaseURL 自定义基址覆盖（代理专有域名或本地端口），非空则覆盖 Environment 预设。
	BaseURL string

	// Timeout HTTP 请求超时，默认 30s。
	Timeout time.Duration
}

// resolveBaseURL 决定最终基址：BaseURL 非空优先，否则取 Environment 预设。
func (c Config) resolveBaseURL() string {
	if c.BaseURL != "" {
		return c.BaseURL
	}
	return c.Environment.BaseURL()
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
		// crypto/rand 在受支持平台几乎不会失败；兜底用时间戳纳秒。
		return hex.EncodeToString([]byte(time.Now().Format("20060102150405.000000000")))
	}
	return hex.EncodeToString(buf)
}
