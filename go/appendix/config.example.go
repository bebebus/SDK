package appendix

// ExampleConfig 是客户端配置模板。复制后替换占位符。
// 正式环境必须 https；勿提交真实密钥。
//
// 代付端点仅在 /api/open/v1（v2 下 payout/* 返回 404）：
// SDK ≥ 1.3.0 的 payout 方法会自动回落 v1。
var ExampleConfig = map[string]any{
	"merchant_no":       "<YOUR_MERCHANT_NO>",
	"api_key":           "<YOUR_API_KEY>",
	"api_secret_pay":    "<YOUR_API_SECRET_PAY>",
	"api_secret_payout": "<YOUR_API_SECRET_PAYOUT>",
	"base_url":          "https://api.<service_domain>/api/open/v2",
	"base_url_payout":   "https://api.<service_domain>/api/open/v1",
	"timeout_ms":        30000,
}
