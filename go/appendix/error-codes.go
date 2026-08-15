package appendix

// ErrorCode 是错误码总表项。业务错误一律 HTTP 200 + 信封 {code, message, data}。
type ErrorCode struct {
	Code      string
	HTTP      int
	Retryable string
	MeaningZh string
	MeaningEn string
	NoteZh    string
	NoteEn    string
}

// ErrorCodes 错误码总表（平台标准项）。
var ErrorCodes = []ErrorCode{
	{Code: "100000", HTTP: 200, Retryable: "depends", MeaningZh: "通用业务失败 / 鉴权失败统一码", MeaningEn: "Generic business failure / unified auth-failure code", NoteZh: "订单未被受理时已实时置 failed，勿用同 out_order_no 重试；鉴权阶段统一为「认证失败」话术", NoteEn: "When the order is not accepted, it is set to failed in real time — do not retry with the same out_order_no; during auth it is a unified authentication-failed message"},
	{Code: "100001", HTTP: 200, Retryable: "no", MeaningZh: "参数校验失败", MeaningEn: "Parameter validation failed", NoteZh: "message 为具体字段校验错误信息（英文，如 `\"amount\" must be a number`）；amount 必须为 JSON 整数，字符串/小数均返回此码。修正参数后重发", NoteEn: "message carries the specific field validation error (in English, e.g. `\"amount\" must be a number`); amount must be a JSON integer — strings/decimals return this code. Fix params and resend"},
	{Code: "100101", HTTP: 200, Retryable: "yes", MeaningZh: "请求过期", MeaningEn: "Request expired", NoteZh: "timestamp 超出 ±300 秒窗口；校准本地时间后重试", NoteEn: "timestamp is outside the ±300s window; calibrate local time and retry"},
	{Code: "100102", HTTP: 200, Retryable: "no", MeaningZh: "IP 不在白名单（message 含商户号与请求 IP）", MeaningEn: "IP not in allowlist (message includes merchant_no and request IP)", NoteZh: "在商户后台把来源 IP 加入白名单后重试；正式密钥与测试密钥共用同一白名单", NoteEn: "Add the source IP to the allowlist in the merchant console and retry; prod and test keys share the same allowlist"},
	{Code: "100103", HTTP: 200, Retryable: "no", MeaningZh: "重放 / 重复请求", MeaningEn: "Replay / duplicate request", NoteZh: "nonce 或签名指纹在 300 秒内重复；换新 nonce（或改参数）后重试", NoteEn: "nonce or signature fingerprint repeated within 300s; use a new nonce (or change params) and retry"},
	{Code: "100104", HTTP: 200, Retryable: "no", MeaningZh: "签名错误", MeaningEn: "Invalid signature", NoteZh: "核对密钥与签名算法（pay 用 api_secret_pay、payout 用 api_secret_payout）", NoteEn: "Check the secret and signing algorithm (pay uses api_secret_pay, payout uses api_secret_payout)"},
	{Code: "100105", HTTP: 200, Retryable: "no", MeaningZh: "IP 在黑名单", MeaningEn: "IP in blocklist", NoteZh: "从黑名单移除来源 IP 后重试", NoteEn: "Remove the source IP from the blocklist and retry"},
	{Code: "100106", HTTP: 200, Retryable: "yes", MeaningZh: "鉴权失败次数过多（限流）", MeaningEn: "Too many auth failures (rate limited)", NoteZh: "同 merchant_no + 来源 IP 在 60 秒内鉴权失败达 60 次触发；等待约 60 秒再试", NoteEn: "Triggered when the same merchant_no + source IP fails auth 60 times within 60s; wait ~60s and retry"},
	{Code: "200002", HTTP: 200, Retryable: "no", MeaningZh: "服务账户已禁用", MeaningEn: "Service account disabled", NoteZh: "联系服务商处理", NoteEn: "Contact your service provider"},
	{Code: "210002", HTTP: 200, Retryable: "no", MeaningZh: "商户已禁用", MeaningEn: "Merchant disabled", NoteZh: "联系服务商处理", NoteEn: "Contact your service provider"},
	{Code: "300101", HTTP: 200, Retryable: "no", MeaningZh: "代收单幂等冲突", MeaningEn: "Collection idempotency conflict", NoteZh: "同 out_order_no 已存在但参数不一致；对齐参数或换新单号", NoteEn: "out_order_no exists with mismatched params; align params or use a new order no."},
	{Code: "300201", HTTP: 200, Retryable: "no", MeaningZh: "代付单幂等冲突", MeaningEn: "Payout idempotency conflict", NoteZh: "同 out_payout_no 已存在但参数不一致；对齐参数或换新单号", NoteEn: "out_payout_no exists with mismatched params; align params or use a new payout no."},
	{Code: "300301", HTTP: 200, Retryable: "no", MeaningZh: "订单不存在", MeaningEn: "Order not found", NoteZh: "核对订单号（order_no / out_order_no / payout_no / out_payout_no）", NoteEn: "Check the order no. (order_no / out_order_no / payout_no / out_payout_no)"},
	{Code: "300401", HTTP: 200, Retryable: "no", MeaningZh: "支付方式不可用 / 不存在", MeaningEn: "Payment method unavailable / missing", NoteZh: "支付方式不可用或不存在", NoteEn: "The payment method is unavailable or does not exist"},
	{Code: "300402", HTTP: 200, Retryable: "yes", MeaningZh: "支付方式配置不可用", MeaningEn: "Payment method configuration unavailable", NoteZh: "当前请求未匹配到有效配置；修改参数或稍后重试", NoteEn: "No valid configuration matched the request; change parameters or retry later"},
	{Code: "300403", HTTP: 200, Retryable: "no", MeaningZh: "费率未配置", MeaningEn: "Fee rate not configured", NoteZh: "联系服务商配置费率后重试", NoteEn: "Contact your service provider to configure the fee rate, then retry"},
	{Code: "300404", HTTP: 200, Retryable: "yes", MeaningZh: "当前条件下无可用支付方式", MeaningEn: "No payment method is available for the request", NoteZh: "v2：channel_code 未授权/币种不一致，或 pay_method 在已授权分组内无金额匹配渠道；v1：country + currency + pay_method 无可用组合。可用 `pay-methods/query` 核对后改参数或稍后重试", NoteEn: "v2: channel_code is unauthorized / currency mismatches, or pay_method has no amount-matching channel in authorized groups; v1: no available country + currency + pay_method combination. Check `pay-methods/query`, then change parameters or retry later"},
	{Code: "300405", HTTP: 200, Retryable: "no", MeaningZh: "缺少必要附加信息（extra.customer）", MeaningEn: "Missing required extra info (extra.customer)", NoteZh: "请按 `data.missing_fields` 补齐 extra.customer 中的字段", NoteEn: "Fill the fields listed in `data.missing_fields` under extra.customer"},
	{Code: "300406", HTTP: 200, Retryable: "no", MeaningZh: "缺少必要下单参数（如 bank_code）", MeaningEn: "Missing required order parameter (e.g. bank_code)", NoteZh: "补齐该路由必填字段后重试", NoteEn: "Fill the required field for that route and retry"},
	{Code: "300407", HTTP: 200, Retryable: "no", MeaningZh: "银行编码非法", MeaningEn: "Invalid bank code", NoteZh: "用「查询可用银行」(payout/banks/query) 返回的 code", NoteEn: "Use a code returned by Query Available Banks (payout/banks/query)"},
	{Code: "300408", HTTP: 200, Retryable: "no", MeaningZh: "当前支付方式不支持代付凭证查询", MeaningEn: "Payment method does not support payout proof query", NoteZh: "当前支付方式不支持凭证查询", NoteEn: "The payment method does not support proof queries"},
	{Code: "300409", HTTP: 200, Retryable: "yes", MeaningZh: "代付凭证暂不可用", MeaningEn: "Payout proof temporarily unavailable", NoteZh: "订单未成功 / 凭证暂未生成 / 超出查询窗口；确认成功且在窗口内再试", NoteEn: "Order is not successful / proof is not ready / outside the query window; confirm success and retry within the window"},
	{Code: "300410", HTTP: 200, Retryable: "yes", MeaningZh: "代付收据暂不可用（订单非 success）", MeaningEn: "Payout receipt temporarily unavailable (order not success)", NoteZh: "等订单出款成功后再试", NoteEn: "Retry after the payout succeeds"},
	{Code: "300411", HTTP: 200, Retryable: "yes", MeaningZh: "收据生成失败", MeaningEn: "Receipt generation failed", NoteZh: "渲染异常；稍后重试", NoteEn: "Rendering error; retry later"},
	{Code: "300501", HTTP: 200, Retryable: "yes", MeaningZh: "余额不足", MeaningEn: "Insufficient balance", NoteZh: "商户可用余额 < 冻结额（amount + fee）；充值后重试", NoteEn: "Available balance < frozen amount (amount + fee); top up and retry"},
}
