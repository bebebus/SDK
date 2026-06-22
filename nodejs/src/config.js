// 环境预设与客户端配置。

// 预设基址：PRODUCTION（文档默认正式地址）与 SANDBOX（本地联调）。
// 真实正式地址按上级代理专有域名派生（https://api.<agent_domain>/api/open/v1），通过 baseUrl 覆盖。
export const Environment = Object.freeze({
  PRODUCTION: 'https://api.project-p-merchant.cniia.cloud/api/open/v1',
  SANDBOX: 'http://127.0.0.1:3090/api/open/v1',
});

// 客户端配置。
//  - merchantNo / apiKey：鉴权
//  - apiSecretPay：pay 类接口与代收/退款回调密钥
//  - apiSecretPayout：payout 类接口与代付回调密钥
//  - environment：Environment.PRODUCTION | Environment.SANDBOX（默认 PRODUCTION）
//  - baseUrl：显式覆盖基址（优先级高于 environment），用于代理专有域名或自定义端口
//  - timeout：单请求超时（毫秒，默认 30000）
export class Config {
  constructor({
    merchantNo,
    apiKey,
    apiSecretPay,
    apiSecretPayout,
    environment = Environment.PRODUCTION,
    baseUrl,
    timeout = 30000,
  } = {}) {
    if (!merchantNo) throw new TypeError('Config: merchantNo is required');
    if (!apiKey) throw new TypeError('Config: apiKey is required');

    this.merchantNo = merchantNo;
    this.apiKey = apiKey;
    this.apiSecretPay = apiSecretPay ?? null;
    this.apiSecretPayout = apiSecretPayout ?? null;

    // baseUrl 显式覆盖优先；否则取环境预设。去掉末尾斜杠，避免拼接出双斜杠。
    const resolved = baseUrl || environment;
    if (!resolved) throw new TypeError('Config: baseUrl or environment is required');
    this.baseUrl = resolved.replace(/\/+$/, '');

    this.timeout = timeout;
  }
}
