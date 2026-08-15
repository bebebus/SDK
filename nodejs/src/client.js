// 商户支付 OpenAPI 客户端：实现全部签名业务端点。
// HTTP 仅用 node:http / node:https；签名用 node:crypto（经 signer.js）。
import http from 'node:http';
import https from 'node:https';
import { URL } from 'node:url';
import { randomUUID } from 'node:crypto';
import { readFileSync } from 'node:fs';

import { sign, verifyCallback as verifyCallbackSig } from './signer.js';

// v2 Base URL 的 pathname 以 /api/open/v2/ 开头时，签名须绑定 METHOD + 规范路径。
function requestBinding(baseUrl, relPath) {
  try {
    const pathname = new URL(String(baseUrl || '').replace(/\/+$/, '') + relPath).pathname;
    if (pathname.startsWith('/api/open/v2/')) {
      return { method: 'POST', path: pathname };
    }
  } catch {
    // 非法 URL 交给后续请求层报错。
  }
  return undefined;
}

// 是否代付类端点（路径以 /merchant/payout 开头）。
function isPayoutPath(relPath) {
  return String(relPath || '').startsWith('/merchant/payout');
}

// 解析本次请求实际使用的基址。
// 契约：代付仅存在于 v1（2026-08-15 拍板）——服务端 v2 Base 下不再注册任何 payout 路由（一律 404）。
// 因此 v2 基址 + payout 路径时自动回落到对应 v1 基址（/api/open/v2 → /api/open/v1），
// 且回落后 requestBinding 对 v1 路径返回 undefined，签名自然是 body-only（无 v2 绑定前缀）。
function resolveRequestBase(baseUrl, relPath) {
  const base = String(baseUrl || '');
  if (!isPayoutPath(relPath)) return base;
  try {
    const pathname = new URL(base.replace(/\/+$/, '') + relPath).pathname;
    if (pathname.startsWith('/api/open/v2/')) {
      // 命中 v2 说明 base 的路径以 /api/open/v2 开头（host 不含斜杠，首个匹配必是路径首段）；
      // 只替换版本号，host 与端口不变。
      return base.replace('/api/open/v2', '/api/open/v1');
    }
  } catch {
    // 非法 URL 交给后续请求层报错。
  }
  return base;
}
import { ApiError, TransportError } from './errors.js';

// [L19] SDK 版本单一事实源：从 package.json 派生（而非硬编码）。
// 优先读 npm 注入的 process.env.npm_package_version（npm scripts 场景），
// 否则解析同包 package.json；任何读取失败兜底（须随发版同步），绝不让 UA 构造抛错。
const SDK_VERSION = (() => {
  if (process.env.npm_package_version) return process.env.npm_package_version;
  try {
    const pkgUrl = new URL('../package.json', import.meta.url);
    const pkg = JSON.parse(readFileSync(pkgUrl, 'utf8'));
    if (pkg && typeof pkg.version === 'string' && pkg.version) return pkg.version;
  } catch {
    // ignore：读不到就走兜底版本号。
  }
  return '1.2.0';
})();
const USER_AGENT = `openapi-sdk-nodejs/${SDK_VERSION}`;

// 去掉值为 null/undefined 的字段（这些字段既不入请求体也不参与签名）。
function dropNullish(obj) {
  const out = {};
  for (const [k, v] of Object.entries(obj)) {
    if (v === null || v === undefined) continue;
    out[k] = v;
  }
  return out;
}

export class Client {
  constructor(config) {
    if (!config) throw new TypeError('Client: config is required');
    this.config = config;
  }

  // ---- 通用底层 ----

  // 构造请求体：注入通用字段 + timestamp + 唯一 nonce，过滤 null/undefined，再用指定密钥签名。
  // secret：'pay' | 'payout'，决定用 api_secret_pay 还是 api_secret_payout。
  _buildBody(params, secretKind, binding) {
    const secret = secretKind === 'payout' ? this.config.apiSecretPayout : this.config.apiSecretPay;
    if (!secret) {
      throw new TypeError(
        `Client: missing ${secretKind === 'payout' ? 'apiSecretPayout' : 'apiSecretPay'} for this endpoint`,
      );
    }

    // 通用字段由 SDK 统一注入，且**始终覆盖**调用方同名字段（故放在 ...params 之后）。
    const merged = {
      ...params,
      merchant_no: this.config.merchantNo,
      api_key: this.config.apiKey,
      timestamp: Math.floor(Date.now() / 1000),
      nonce: randomUUID(),
    };
    const body = dropNullish(merged);
    body.sign = sign(body, secret, binding);
    return body;
  }

  // 发起 POST application/json 请求，解析统一信封 {code,message,data}。
  // 返回 { data, raw }；code !== 0 抛 ApiError；HTTP/网络错误抛 TransportError。
  async _post(path, params, secretKind) {
    // 代付仅存在于 v1（2026-08-15 拍板）：v2 基址下 payout 请求自动回落 v1，
    // 回落后 requestBinding 返回 undefined → body-only 签名；代收路径基址不变。
    const base = resolveRequestBase(this.config.baseUrl, path);
    const body = this._buildBody(params, secretKind, requestBinding(base, path));
    const payload = JSON.stringify(body);
    const url = new URL(base + path);
    const lib = url.protocol === 'https:' ? https : http;

    const raw = await new Promise((resolve, reject) => {
      const req = lib.request(
        url,
        {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            'Content-Length': Buffer.byteLength(payload),
            Accept: 'application/json',
            // 自识别 User-Agent：避免被 WAF/CDN（如 Cloudflare）按默认/空 UA 拦成 403。
            // 版本号从 package.json 单一派生（见 SDK_VERSION）。
            'User-Agent': USER_AGENT,
          },
          timeout: this.config.timeout,
        },
        (res) => {
          const chunks = [];
          res.on('data', (c) => chunks.push(c));
          res.on('end', () => {
            const text = Buffer.concat(chunks).toString('utf8');
            const status = res.statusCode || 0;
            if (status < 200 || status >= 300) {
              reject(
                new TransportError(`HTTP ${status} from ${path}`, {
                  statusCode: status,
                  body: text,
                }),
              );
              return;
            }
            let parsed;
            try {
              parsed = JSON.parse(text);
            } catch (e) {
              reject(
                new TransportError(`Invalid JSON response from ${path}`, {
                  statusCode: status,
                  body: text,
                  cause: e,
                }),
              );
              return;
            }
            resolve(parsed);
          });
        },
      );

      req.on('timeout', () => {
        // destroy 触发 'error'，统一在下方转 TransportError。
        req.destroy(new Error(`request to ${path} timed out after ${this.config.timeout}ms`));
      });
      req.on('error', (e) => {
        reject(new TransportError(`Network error calling ${path}: ${e.message}`, { cause: e }));
      });

      req.write(payload);
      req.end();
    });

    if (!raw || typeof raw !== 'object') {
      throw new TransportError(`Unexpected response shape from ${path}`, { body: String(raw) });
    }
    if (raw.code !== 0) {
      throw new ApiError(raw.code, raw.message, raw.data, raw);
    }
    // 同时暴露原始信封，便于调用方拿 message 等。
    return { data: raw.data ?? null, raw };
  }

  // ---- 代收（Pay，密钥：api_secret_pay）----

  // 代收下单。params: { out_order_no, amount(int), currency, notify_url,
  //   channel_code? | pay_method?, country?, return_url?, subject?, remark?, client_ip?, extra? }
  // v2：channel_code 与 pay_method 二选一；v1 仍要求 pay_method。
  payCreate(params) {
    return this._post('/merchant/pay/create', params, 'pay');
  }

  // 代收查单。params: { order_no? | out_order_no? }（二选一，至少一个）
  payQuery(params) {
    return this._post('/merchant/pay/query', params, 'pay');
  }

  // 可用支付方式 / 分组编码。params: { country?, biz_type?, currency? }
  // v2 返回 data.pay_methods[] 与 data.channel_codes[]；v1 仍为 data.methods[]。
  payMethodsQuery(params = {}) {
    return this._post('/merchant/pay-methods/query', params, 'pay');
  }

  // 兼容别名：仍打 /merchant/groups/query。新对接请用 payMethodsQuery 的 channel_codes。
  groupsQuery(params = {}) {
    return this._post('/merchant/groups/query', params, 'pay');
  }

  // 余额查询。params: { currency? }
  balanceQuery(params = {}) {
    return this._post('/merchant/balance/query', params, 'pay');
  }

  // 代收测试单完成（仅测试密钥）。params: { order_no? | out_order_no?, result('success'|'failed'), actual_amount?(int) }
  payTestComplete(params) {
    return this._post('/merchant/pay/test/complete', params, 'pay');
  }

  // ---- 代付（Payout，密钥：api_secret_payout）----
  // 代付端点仅注册于 v1 Base（2026-08-15 拍板，v2 下 payout 路由一律 404）；
  // 本 SDK 在 v2 baseUrl 下对以下方法自动回落 v1 基址并使用 body-only 签名（见 resolveRequestBase）。

  // 代付下单。params: { out_payout_no, amount(int), currency, notify_url, account_no,
  //   pay_method(必填), country?, account_name?, bank_code?, bank_name?, remark?, client_ip?, extra? }
  // v1 契约：pay_method 必填，不收 channel_code/group_code。
  payoutCreate(params) {
    return this._post('/merchant/payout/create', params, 'payout');
  }

  // 代付查单。params: { payout_no? | out_payout_no? }
  payoutQuery(params) {
    return this._post('/merchant/payout/query', params, 'payout');
  }

  // 可用银行。params: { pay_method, country?, currency? }
  payoutBanksQuery(params) {
    return this._post('/merchant/payout/banks/query', params, 'payout');
  }

  // 代付凭证查询（仅 status=success）。params: { payout_no? | out_payout_no? }
  payoutProofQuery(params) {
    return this._post('/merchant/payout/proof/query', params, 'payout');
  }

  // 代付收据。params: { payout_no? | out_payout_no?, lang?('en'|'zh-CN'|'zh-TW'), inline?(bool|0|1) }
  // inline 以整数 1/0 发送（避免布尔签名歧义）。
  payoutReceiptQuery(params) {
    const p = { ...params };
    if ('inline' in p && p.inline !== null && p.inline !== undefined) {
      p.inline = p.inline ? 1 : 0;
    }
    return this._post('/merchant/payout/receipt/query', p, 'payout');
  }

  // 代付测试单完成（仅测试密钥）。params: { payout_no? | out_payout_no?, result('success'|'failed') }
  payoutTestComplete(params) {
    return this._post('/merchant/payout/test/complete', params, 'payout');
  }

  // ---- 回调验签 ----

  // 代收/退款回调验签（api_secret_pay）。payload：解析后的回调键值表（含 sign）。
  verifyPayCallback(payload) {
    if (!this.config.apiSecretPay) throw new TypeError('Client: missing apiSecretPay');
    return verifyCallbackSig(payload, this.config.apiSecretPay);
  }

  // 代付回调验签（api_secret_payout）。
  verifyPayoutCallback(payload) {
    if (!this.config.apiSecretPayout) throw new TypeError('Client: missing apiSecretPayout');
    return verifyCallbackSig(payload, this.config.apiSecretPayout);
  }
}
