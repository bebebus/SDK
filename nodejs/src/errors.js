// SDK 异常类型。
//  - ApiError：服务端返回 code !== 0 的业务错误，携带 code/message/data 及原始响应。
//  - TransportError：HTTP/网络层错误（连接失败、超时、非 2xx、响应非合法 JSON 等）。

export class ApiError extends Error {
  constructor(code, message, data, raw) {
    super(message || `API error (code=${code})`);
    this.name = 'ApiError';
    this.code = code;
    this.data = data ?? null;
    // 原始统一信封 { code, message, data }，供调用方自行判断。
    this.raw = raw ?? null;
  }
}

export class TransportError extends Error {
  // statusCode：HTTP 状态码（若有）；body：原始响应文本（若有）；cause：底层错误（若有）。
  constructor(message, { statusCode = null, body = null, cause = null } = {}) {
    super(message);
    this.name = 'TransportError';
    this.statusCode = statusCode;
    this.body = body;
    if (cause) this.cause = cause;
  }
}
