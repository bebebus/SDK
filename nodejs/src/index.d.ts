// 商户支付 OpenAPI Node.js SDK —— TypeScript 类型声明（手写，覆盖全部公开 API）。
// [L24] 公开 SDK 自带类型；仅声明类型，不改变任何运行时 .js 行为。
// 字段契约见仓库根 INTERFACES.md，签名算法见 SIGNING.md。

/** 预设环境：PRODUCTION 无内置基址（必须显式传 baseUrl）；SANDBOX 为本地联调基址。 */
export const Environment: {
  /** 正式：无内置 URL，构造 Config 时必须显式传 baseUrl（按上级代理专有域名派生）。 */
  readonly PRODUCTION: null;
  /** 本地/联调：http://127.0.0.1:3090/api/open/v1 */
  readonly SANDBOX: string;
};

/** Environment 取值类型（null 或预设字符串）。 */
export type EnvironmentValue = (typeof Environment)[keyof typeof Environment];

/** 客户端配置入参。 */
export interface ConfigOptions {
  /** 商户号（必填）。 */
  merchantNo: string;
  /** API Key（必填）。 */
  apiKey: string;
  /** 代收/退款密钥 api_secret_pay：pay 类接口与代收/退款回调用。只跑代付可不传。 */
  apiSecretPay?: string | null;
  /** 代付密钥 api_secret_payout：payout 类接口与代付回调用。只跑代收可不传。 */
  apiSecretPayout?: string | null;
  /** 预设环境（默认 PRODUCTION）。 */
  environment?: EnvironmentValue;
  /** 显式基址，优先级高于 environment；PRODUCTION 无内置 URL，必须显式传入。非 localhost 强制 https。 */
  baseUrl?: string;
  /** 单请求超时（毫秒，默认 30000）。 */
  timeout?: number;
}

/** 客户端配置。非 localhost 基址强制 https，否则构造抛 TypeError。 */
export class Config {
  constructor(options?: ConfigOptions);
  readonly merchantNo: string;
  readonly apiKey: string;
  readonly apiSecretPay: string | null;
  readonly apiSecretPayout: string | null;
  /** 已去除末尾斜杠的最终基址。 */
  readonly baseUrl: string;
  readonly timeout: number;
}

/** 业务错误：服务端返回 code !== 0 时抛出，携带原始信封。 */
export class ApiError extends Error {
  constructor(code: number, message?: string, data?: unknown, raw?: unknown);
  readonly name: 'ApiError';
  /** 业务错误码（见 INTERFACES.md 错误码表）。 */
  readonly code: number;
  /** 业务数据（可空）。 */
  readonly data: unknown | null;
  /** 原始统一信封 { code, message, data }。 */
  readonly raw: unknown | null;
}

/** 传输层错误：HTTP 非 2xx、连接失败、超时、响应非合法 JSON 等。 */
export class TransportError extends Error {
  constructor(
    message: string,
    options?: { statusCode?: number | null; body?: string | null; cause?: unknown },
  );
  readonly name: 'TransportError';
  /** HTTP 状态码（若有）。 */
  readonly statusCode: number | null;
  /** 原始响应文本（若有）。 */
  readonly body: string | null;
  /** 底层错误（若有）。 */
  readonly cause?: unknown;
}

/** 统一响应信封。 */
export interface Envelope<T = unknown> {
  code: number;
  message?: string;
  data?: T;
}

/** 端点统一返回：data 为业务数据，raw 为原始信封。 */
export interface ApiResult<T = Record<string, unknown>> {
  /** 业务数据（成功时；可能为 null）。 */
  data: T | null;
  /** 原始统一信封，便于读取 message 等。 */
  raw: Envelope<T>;
}

/** 任意业务请求参数（键值表，值参与签名）。具体字段见 INTERFACES.md。 */
export type Params = Record<string, unknown>;

/** 回调键值表（含 sign 字段）。 */
export type CallbackPayload = Record<string, unknown>;

/** 商户支付 OpenAPI 客户端：覆盖全部 11 个签名业务端点 + 回调验签。 */
export class Client {
  constructor(config: Config);
  readonly config: Config;

  // ---- 代收（Pay，密钥 api_secret_pay）----

  /** 代收下单 POST /merchant/pay/create。 */
  payCreate(params: Params): Promise<ApiResult>;
  /** 代收查单 POST /merchant/pay/query（order_no 或 out_order_no 二选一）。 */
  payQuery(params: Params): Promise<ApiResult>;
  /** 可用支付方式 POST /merchant/pay-methods/query。 */
  payMethodsQuery(params?: Params): Promise<ApiResult>;
  /** 余额查询 POST /merchant/balance/query。 */
  balanceQuery(params?: Params): Promise<ApiResult>;
  /** 代收测试单完成 POST /merchant/pay/test/complete（仅测试密钥）。 */
  payTestComplete(params: Params): Promise<ApiResult>;

  // ---- 代付（Payout，密钥 api_secret_payout）----

  /** 代付下单 POST /merchant/payout/create。 */
  payoutCreate(params: Params): Promise<ApiResult>;
  /** 代付查单 POST /merchant/payout/query（payout_no 或 out_payout_no 二选一）。 */
  payoutQuery(params: Params): Promise<ApiResult>;
  /** 可用银行 POST /merchant/payout/banks/query。 */
  payoutBanksQuery(params: Params): Promise<ApiResult>;
  /** 代付凭证查询 POST /merchant/payout/proof/query（仅 status=success）。 */
  payoutProofQuery(params: Params): Promise<ApiResult>;
  /** 代付收据 POST /merchant/payout/receipt/query（inline 以整数 1/0 发送）。 */
  payoutReceiptQuery(params: Params & { inline?: boolean | 0 | 1 }): Promise<ApiResult>;
  /** 代付测试单完成 POST /merchant/payout/test/complete（仅测试密钥）。 */
  payoutTestComplete(params: Params): Promise<ApiResult>;

  // ---- 回调验签 ----

  /** 代收/退款回调验签（api_secret_pay，时序安全比较）。 */
  verifyPayCallback(payload: CallbackPayload): boolean;
  /** 代付回调验签（api_secret_payout，时序安全比较）。 */
  verifyPayoutCallback(payload: CallbackPayload): boolean;
}

// ---- 签名器（底层，按需直接使用）----

/** 计算签名：HMAC-SHA256(base, key=secret) -> hex 小写。空密钥抛 TypeError。 */
export function sign(payload: Record<string, unknown>, secret: string): string;

/** 构造签名 base 串（不含 HMAC），便于逐字节断言。 */
export function buildSignBase(payload: Record<string, unknown>, secret: string): string;

/** 稳定 JSON 序列化（递归、key 升序、紧凑无空格），与跨语言实现逐字节一致。 */
export function stableStringify(value: unknown): string;

/** 回调验签（时序安全比较）。非法输入/空密钥一律返回 false，绝不抛异常。 */
export function verifyCallback(payload: CallbackPayload, secret: string): boolean;
