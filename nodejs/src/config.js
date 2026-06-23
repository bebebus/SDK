// 环境预设与客户端配置。
import { URL } from 'node:url';

// [D 传输 https] 校验基址传输安全：
//  - localhost / 127.0.0.1 / ::1 放行 http（本地联调）；
//  - 其余主机强制 https://，否则拒绝（防止凭证/签名明文上链路被中间人窃取或篡改）。
// 解析失败（非法 URL）同样拒绝，给出清晰错误。
function assertSecureBaseUrl(resolved) {
  let parsed;
  try {
    parsed = new URL(resolved);
  } catch {
    throw new TypeError(`Config: baseUrl is not a valid URL: ${resolved}`);
  }
  const host = parsed.hostname;
  // URL 解析 IPv6 主机名带方括号（如 [::1]），归一去掉后再比较。
  const normalizedHost = host.replace(/^\[|\]$/g, '');
  const isLocal =
    normalizedHost === 'localhost' || normalizedHost === '127.0.0.1' || normalizedHost === '::1';
  if (parsed.protocol === 'https:') return;
  if (parsed.protocol === 'http:' && isLocal) return;
  throw new TypeError(
    `Config: baseUrl must use https:// for non-localhost hosts (got ${parsed.protocol}//${host}); ` +
      'plaintext http is only allowed for localhost/127.0.0.1 local debugging',
  );
}

// 预设：PRODUCTION（正式，无内置 URL）与 SANDBOX（本地联调）。
// 正式地址按上级代理专有域名派生（https://api.<agent_domain>/api/open/v1），必须通过 baseUrl 显式传入。
export const Environment = Object.freeze({
  PRODUCTION: null,
  SANDBOX: 'http://127.0.0.1:3090/api/open/v1',
});

// 客户端配置。
//  - merchantNo / apiKey：鉴权
//  - apiSecretPay：pay 类接口与代收/退款回调密钥
//  - apiSecretPayout：payout 类接口与代付回调密钥
//  - environment：Environment.PRODUCTION | Environment.SANDBOX（默认 PRODUCTION）
//  - baseUrl：显式覆盖基址（优先级高于 environment）；PRODUCTION 无内置 URL，必须显式传入
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

    // baseUrl 显式覆盖优先；否则取环境预设（PRODUCTION 无内置 URL）。
    // 若最终基址为空（选了 PRODUCTION 又没传 baseUrl）→ 抛清晰错误。
    const resolved = baseUrl || environment;
    if (!resolved) {
      throw new TypeError(
        'Config: baseUrl is required: production base URL is provided per your agent domain ' +
          '(e.g. https://api.<agent_domain>/api/open/v1)',
      );
    }
    // [D 传输 https] 非 localhost 必须 https，否则拒绝（localhost 放行 http 兼容本地联调）。
    assertSecureBaseUrl(resolved);
    // 去掉末尾斜杠，避免拼接出双斜杠。
    this.baseUrl = resolved.replace(/\/+$/, '');

    this.timeout = timeout;
  }
}
