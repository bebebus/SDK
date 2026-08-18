> 中文 | [English](./SIGNING.en.md)

# 签名规范（所有语言 SDK 的单一事实源）

商户 OpenAPI 的请求与回调都用 **HMAC-SHA256** 签名，输出 **十六进制小写**。本文件定义的算法
与服务端签名实现**逐字节一致**；五套 SDK
的签名器都必须能复现 [`test-vectors.json`](./test-vectors.json) 里的 `base` 与 `sign`，否则即视为不合格。

> 签名是整套 SDK 唯一「一个字节都不能错」的部分。多数跨语言失败都出在**嵌套 JSON 序列化**与
> **标量强转字符串**两处，务必逐条对照下文「跨语言坑」。

## 一、算法步骤

输入：业务字段构成的键值表 `payload`（含 `merchant_no/api_key/timestamp` 等通用字段，但**不含** `sign`）、密钥 `secret`。

1. **过滤**：丢弃键名为 `sign` 的字段；丢弃值为 `null`/`undefined`（各语言对应 `None`/`nil`/`null`）的字段。
2. **排序**：剩余字段按**键名 ASCII（码点）升序**排序（等价 JS `Array.prototype.sort()` 默认行为、Go `sort.Strings`、Python `sorted()`、Java `String.compareTo`）。
3. **取值字符串** `valueForSign(v)`：
   - `v` 是 **object / array** → 用下文「稳定 JSON 序列化」`stableStringify(v)`。
   - `v` 是**标量**（string/number/boolean）→ 取**原始字符串形态**（见下「标量规则」），**不加引号**。
4. **拼接 base**：把每个字段拼成 `key=value`，用 `&` 连接，**末尾追加** `&secret=<secret>`：
   ```
   k1=v1&k2=v2&...&kN=vN&secret=<secret>
   ```
5. **计算 sign**：`HMAC_SHA256(base, key=secret)`，输出**十六进制小写**字符串。
6. **密钥选择**：pay 类接口与代收回调用 `api_secret_pay`；payout 类接口与代付回调用 `api_secret_payout`。
   （HMAC 的 key 与 base 末尾的 `&secret=` 用的是**同一个** secret。）

把算出的 `sign` 放回请求体一起发送。

## 二、标量规则（顶层）

顶层标量直接转字符串、**不加引号**，且必须与 JS `String(v)` 完全一致：

| 类型 | 规则 | 例 |
|------|------|----|
| string | 原样输出（不转义、不加引号、不 URL 编码） | `订单/支付 <A&B>` → `订单/支付 <A&B>` |
| 整数 | 十进制无符号/有符号文本 | `10000` → `10000` |
| 布尔 | **必须** `true` / `false`（小写） | `true` → `true` |

> ⚠️ 顶层 string 含 `&`、`=`、空格、中文都**原样**保留（不转义）。base 因此可能"看起来有歧义"，
> 但服务端按同样规则构造，验签自洽，无需担心。

> ⚠️ API 不使用浮点金额——所有金额是**最小单位整数**（`10000 = 1 元`）。SDK 的金额参数请用整数类型，
> 避免任何浮点格式化的跨语言差异。

## 三、稳定 JSON 序列化（嵌套 object/array）

`stableStringify(value)` 递归规则（必须与 JS `JSON.stringify` + key 升序一致）：

- `null` → `null`
- 标量（string/number/boolean）→ `JSON.stringify(v)`：
  - string → **带双引号**，并做 JSON 转义（见下）
  - number → 数字字面量（`123`、`1.5`），无引号
  - boolean → `true` / `false`
- array → `[` + 各元素递归 `stableStringify` 用 `,` 连接 + `]`（**保持元素顺序**）
- object → `{` + 键名**升序**后，每对 `JSON.stringify(key) + ":" + stableStringify(value)` 用 `,` 连接 + `}`（**紧凑无空格**）

### JSON 字符串转义（嵌套字符串值，须对齐 JS `JSON.stringify`）

- 转义：`"` → `\"`，`\` → `\\`，`\b \f \n \r \t` → 对应 `\b \f \n \r \t`，其余 U+0000–U+001F 控制字符 → `\u00XX`（小写 hex，四位）。
- **不转义**：`/`（正斜杠保持原样）、`<` `>` `&`（不做 HTML 转义）、所有非 ASCII（中文/emoji 等保持原样 UTF-8，**不**转成 `\uXXXX`）。

**反例锚定**：嵌套字符串 `中文"<>&/\` + 换行 + 制表 + `末` 必须序列化为
`"中文\"<>&/\\\n\t末"`（中文与 `/` `<>&` 原样、`"` 与 `\` 转义、换行制表转 `\n` `\t`）。
对应向量 `unicode_and_special_chars`。

## 四、跨语言坑（逐语言落地要点）

| 语言 | 嵌套 JSON 序列化（必须配置） | 顶层标量强转（必须特判） |
|------|------------------------------|--------------------------|
| **Node.js** | `JSON.stringify` + 自写 key 升序递归（`JSON.stringify` 不保证 key 序）。默认转义行为已对：不转 `/`、不转非 ASCII、不转 `<>&`。 | `String(v)` 即对：`String(true)="true"`、`String(10000)="10000"`。 |
| **Python** | `json.dumps(v, ensure_ascii=False, separators=(',',':'), sort_keys=True)`。**必须** `ensure_ascii=False`（否则非 ASCII 变 `\uXXXX`）。Python 不转义 `/`、`<>&`，与 JS 一致。 | `str(True)=="True"` ✗ → **特判** bool→`"true"/"false"`（且因 `bool` 是 `int` 子类，判断顺序须先 bool 再 int）。 |
| **PHP** | `json_encode(v, JSON_UNESCAPED_SLASHES \| JSON_UNESCAPED_UNICODE)` + 自写 key 升序（PHP 关联数组需 `ksort` 递归）。默认 `json_encode` 会转 `/` 和非 ASCII，**必须**加这两个 flag。`<>&` 默认不转（除非加 `JSON_HEX_*`，不要加）。 | `(string)true=="1"` ✗、`(string)false==""` ✗ → **特判** bool→`"true"/"false"`。整数 `(string)10000` 正确。 |
| **Java** | 无内建 JSON：**自写** `stableStringify`（递归、key 升序、紧凑），字符串转义严格按上节实现（只转 `" \ \b\f\n\r\t` 与其余控制字符 `\u00XX`，**不**转 `/`、非 ASCII、`<>&`）。 | `String.valueOf(true)=="true"` 正确；整数用 `Long.toString`/`BigInteger.toString`。注意按值类型分派。 |
| **Go** | `encoding/json`：**必须** `Encoder.SetEscapeHTML(false)`（默认转 `<>&` 为 `<` 等）。Go 默认**不**转 `/`、**不**转非 ASCII（与 JS 一致）。需自写 key 升序递归（`json.Marshal` 对 `map` 会自动按 key 升序，但为可控建议显式递归）。处理来自 JSON 的数字用 `json.Number`（`Decoder.UseNumber()`）避免 `float64` 把大整数写成 `1e+12`。 | `strconv.FormatBool(true)=="true"` 正确；整数 `strconv.FormatInt`。`fmt.Sprint(float64)` 对大整数会出科学计数法，**勿**用浮点承载金额。 |

> 通用：**HMAC 与 base 的 secret 是同一个**；输出 hex **小写**；UTF-8 编码后再算 HMAC。

## 五、Worked Example

向量 `pay_create_scalars`，`secret = sk_test_0123456789abcdef0123456789abcdef`：

base：
```
amount=10000&api_key=ak_demo_key&country=PH&currency=PHP&merchant_no=M00000001&notify_url=https://merchant.example.com/api/notify/pay&out_order_no=202501010001&pay_method=gcash&timestamp=1736073600&secret=sk_test_0123456789abcdef0123456789abcdef
```
sign：
```
9b65f090a2032f0241ba0587aad36768bed923b7543d711acbad0021d2f60568
```

## 六、回调验签（通用、字段无关）

服务会向商户 `notify_url` POST 一段 JSON 回调，体内含 `sign` 与若干业务字段。验签步骤：

1. 解析回调 JSON 为键值表。
2. **取出该表里除 `sign` 外的所有顶层字段**（不要硬编码字段清单——回调字段可能新增或减少；只要“除 sign 外全参与”即可保持兼容）。
3. 用本文算法算出期望 `sign`（代收/退款回调用 `api_secret_pay`，代付回调用 `api_secret_payout`）。
4. 与回调里的 `sign` **时序安全比较**（`crypto.timingSafeEqual` / `hmac.compare_digest` / `MessageDigest.isEqual` / `hmac.Equal` / `hash_equals`）。
5. 验签失败 → 拒绝处理，不要回成功应答；同一订单可能再次收到回调。

### 商户应答（决定是否继续收到回调）

只要 HTTP 状态码为 2xx，且响应体符合下列任一格式，即视为成功应答：

- 纯文本 `success` / `ok`（**大小写不敏感**，trim 后整段匹配）
- JSON `{"success": true}`
- JSON `{"code": 0}`
- JSON `{"message": "success"}` 或 `{"message":"ok"}`（大小写不敏感）
- JSON 字符串 `"success"` / `"ok"`

否则视为失败并重试（指数退避，约 `1m,2m,5m,10m,30m,60m` 共 6 次）。SDK 回调示例统一回 HTTP 200 + 纯文本 `success`。
处理务必**幂等**（同一订单可能被回调多次）。
