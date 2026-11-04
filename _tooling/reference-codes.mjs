import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { readFileSync, writeFileSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
export const reference = JSON.parse(readFileSync(resolve(root, 'appendix/reference-codes.json'), 'utf8'));
const text = (value) => typeof value === 'string' && value.trim().length > 0;
const unique = (rows, key) => assert.equal(new Set(rows.map(key)).size, rows.length, '代码不能重复');

export function validateReference(data) {
  assert.deepEqual(Object.keys(data).sort(), [
    'banks',
    'countries',
    'currencies',
    'schema_version',
    'source',
    'updated_at',
  ]);
  assert.equal(data.schema_version, 1);
  assert.match(data.updated_at, /^\d{4}-\d{2}-\d{2}$/);
  assert.match(data.source.revision, /^[a-f0-9]{40}$/);
  assert.equal(data.source.project, 'project-p');
  const sources = new Set(data.source.files.map((file) => file.path));
  unique(data.source.files, (file) => file.path);
  for (const file of data.source.files) {
    assert.match(file.path, /^(api|web-shared)\/[\w./-]+$/);
    assert.ok(!file.path.split('/').includes('..'));
    assert.match(file.sha256, /^[a-f0-9]{64}$/);
  }
  for (const name of ['countries', 'currencies', 'banks']) assert.ok(data[name].length > 0);
  unique(data.countries, (c) => c.code);
  unique(data.currencies, (c) => c.code);
  unique(data.banks, (b) => `${b.country}|${b.bank_code}`);
  const currencies = new Set(data.currencies.map((c) => c.code));
  const countries = new Set(data.countries.map((c) => c.code));
  for (const c of data.currencies) {
    assert.match(c.code, c.kind === 'crypto' ? /^USDT$/ : /^[A-Z]{3}$/);
    assert.ok(text(c.name_zh) && text(c.name_en));
    assert.ok(['fiat', 'crypto'].includes(c.kind));
    assert.deepEqual(Object.keys(c).sort(), ['code', 'kind', 'name_en', 'name_zh']);
  }
  for (const c of data.countries) {
    assert.match(c.code, /^[A-Z]{2}$/);
    assert.ok(text(c.name_zh) && text(c.name_en));
    assert.ok(c.currencies.length > 0);
    unique(c.currencies, (value) => value);
    for (const currency of c.currencies) assert.ok(currencies.has(currency), `未知币种 ${currency}`);
    assert.deepEqual(Object.keys(c).sort(), ['code', 'currencies', 'name_en', 'name_zh']);
  }
  for (const bank of data.banks) {
    assert.ok(countries.has(bank.country), `未知国家 ${bank.country}`);
    assert.match(bank.bank_code, /^[A-Z0-9_]+$/);
    assert.ok(text(bank.bank_name));
    assert.ok(sources.has(bank.source), `未知来源 ${bank.source}`);
    // 严格白名单：禁止把 upstream / clearing / 凭据等内部字段带进公共数据。
    assert.deepEqual(Object.keys(bank).sort(), ['bank_code', 'bank_name', 'country', 'source']);
  }
}

const md = (value) => String(value).replaceAll('|', '\\|').replaceAll('\n', '<br>');
const table = (headers, rows) =>
  [headers, headers.map(() => '---'), ...rows].map((row) => `| ${row.map(md).join(' | ')} |`).join('\n');
const csvCell = (value) => {
  let s = Array.isArray(value) ? value.join(';') : String(value);
  if (/^[\s]*[=+@-]/.test(s)) s = `'${s}`;
  return `"${s.replaceAll('"', '""')}"`;
};
export const csv = (headers, rows) =>
  '\uFEFF' + [headers, ...rows].map((row) => row.map(csvCell).join(',')).join('\r\n') + '\r\n';
const quote = JSON.stringify;
const phpQuote = (value) => `'${value.replaceAll('\\', '\\\\').replaceAll("'", "\\'")}'`;

export function buildFiles(data = reference) {
  validateReference(data);
  const { countries, currencies, banks } = data;
  const catalog = JSON.stringify({ countries, currencies }, null, 2) + '\n';
  const countriesTable = table(
    ['country', '名称', '英文名称', 'currency'],
    countries.map((c) => [c.code, c.name_zh, c.name_en, c.currencies.join(', ')]),
  );
  const currenciesTable = table(
    ['currency', '名称', '英文名称', '类型'],
    currencies.map((c) => [c.code, c.name_zh, c.name_en, c.kind]),
  );
  const bankSections = countries
    .filter((c) => banks.some((b) => b.country === c.code))
    .map((c) => {
      const rows = banks.filter((b) => b.country === c.code);
      return (
        `### ${c.name_zh}（${c.code}，${rows.length} 项）\n\n` +
        table(
          ['country', 'bank_code', 'bank_name'],
          rows.map((b) => [b.country, b.bank_code, b.bank_name]),
        )
      );
    });
  const doc = `<!-- 由 node _tooling/reference-codes.mjs 生成，请修改 reference-codes.json 后重新生成。 -->
# 国家、币种、银行代码参考表

更新日期：${data.updated_at}。共 ${countries.length} 个国家/地区、${currencies.length} 个币种、${banks.length} 个银行/机构代码。

本表记录项目标准字典与区域码册，包含预登记、历史银行和钱包机构，不表示商户已获授权或当前可用。国家/地区以 \`countries/query\` 返回为准；下单组合以 \`pay-methods/query\` 返回为准；代付银行必须从 \`payout/banks/query\` 获取。调用方法与鉴权见 [接口文档](../INTERFACES.md)。

## 下载与字段约定

- [完整 JSON](./reference-codes.json)：唯一维护源；\`source\` 保存源项目提交和文件 SHA-256，银行项的 \`source\` 为源文件路径。
- CSV：[国家](./countries.csv)、[币种](./currencies.csv)、[银行代码](./banks.csv)。UTF-8 BOM、CRLF；国家的多币种以分号分隔；疑似公式单元格加单引号。
- \`country\` 使用 ISO 3166-1 alpha-2；\`currency\` 使用 ISO 4217，USDT 为平台加密货币例外。所有币种金额均按实际金额 × 10000 传整数。
- 银行唯一键为 \`(country, bank_code)\`，不能仅按银行代码跨国家合并；\`bank_code\` 是平台助记码，保留原始大小写，不是 SWIFT、清算行号或上游私有码。
- \`bank_name\` 沿用源码册机构名，可能含英文或繁体字。HK 的 FPS 是转账轨道，排除在银行清单外。PH 码册内的 USDTTRC / USDTERC 为历史目的地代码，不代表币种或新支付方式。
- MAYA / MAYABANK / MAYABUSINESS、KBZPAY / KBZ、CBPAY / CB、AYAPAY / AYA 等为不同资金目的地，不应互换。
- 未列出银行的国家表示当前源码册未收录，不能据此判断商户不可用。香港为区域扩展项，部署不一定预置；其他国家来自平台基线。

## 国家/地区

${countriesTable}

## 币种

${currenciesTable}

## 银行/机构代码

${bankSections.join('\n\n')}

## 来源与更新

源项目：\`${data.source.project}\`；源码提交：\`${data.source.revision}\`。文件指纹见 JSON 的 \`source.files\`。字典不是运行中数据库或商户实时授权的快照。

维护步骤见 [贡献指南](../CONTRIBUTING.md#参考代码表维护)。只编辑 \`appendix/reference-codes.json\`，运行 \`node _tooling/reference-codes.mjs\` 同步 Markdown、CSV 和五种语言的国家/币种附录；运行 \`node _tooling/reference-codes.mjs --check\` 校验生成结果。
`;
  const php = `<?php

declare(strict_types=1);

// 由 _tooling/reference-codes.mjs 生成；数据源 appendix/reference-codes.json。
// 标准字典与区域扩展，实际可用范围以商户查询接口为准。
return [
    'countries' => [
${countries.map((c) => `        ['code' => ${phpQuote(c.code)}, 'name_zh' => ${phpQuote(c.name_zh)}, 'name_en' => ${phpQuote(c.name_en)}, 'currencies' => [${c.currencies.map(phpQuote).join(', ')}]],`).join('\n')}
    ],
    'currencies' => [
${currencies.map((c) => `        ['code' => ${phpQuote(c.code)}, 'name_zh' => ${phpQuote(c.name_zh)}, 'name_en' => ${phpQuote(c.name_en)}, 'kind' => ${phpQuote(c.kind)}],`).join('\n')}
    ],
];
`;
  const pythonRows = (rows) =>
    rows
      .map(
        (row) =>
          `    {${Object.entries(row)
            .map(
              ([key, value]) =>
                `${quote(key)}: ${Array.isArray(value) ? `[${value.map(quote).join(', ')}]` : quote(value)}`,
            )
            .join(', ')}},`,
      )
      .join('\n');
  const python = `# 由 _tooling/reference-codes.mjs 生成；数据源 appendix/reference-codes.json。
# 标准字典与区域扩展，实际可用范围以商户查询接口为准。

COUNTRIES = [
${pythonRows(countries)}
]

CURRENCIES = [
${pythonRows(currencies)}
]
`;
  const go = `package appendix

// 由 _tooling/reference-codes.mjs 生成；数据源 appendix/reference-codes.json。

// Country 是国家/地区参考项。
type Country struct {
\tCode       string
\tNameZh     string
\tNameEn     string
\tCurrencies []string
}

// Currency 是币种参考项。
type Currency struct {
\tCode   string
\tNameZh string
\tNameEn string
\tKind   string
}

// Countries 包含标准字典与区域扩展，实际可用范围以商户查询接口为准。
var Countries = []Country{
${countries.map((c) => `\t{Code: ${quote(c.code)}, NameZh: ${quote(c.name_zh)}, NameEn: ${quote(c.name_en)}, Currencies: []string{${c.currencies.map(quote).join(', ')}}},`).join('\n')}
}

// Currencies 是币种参考字典。
var Currencies = []Currency{
${currencies.map((c) => `\t{Code: ${quote(c.code)}, NameZh: ${quote(c.name_zh)}, NameEn: ${quote(c.name_en)}, Kind: ${quote(c.kind)}},`).join('\n')}
}
`;
  return new Map([
    ['appendix/REFERENCE-CODES.md', doc],
    [
      'appendix/countries.csv',
      csv(
        ['country', 'name_zh', 'name_en', 'currencies'],
        countries.map((c) => [c.code, c.name_zh, c.name_en, c.currencies]),
      ),
    ],
    [
      'appendix/currencies.csv',
      csv(
        ['currency', 'name_zh', 'name_en', 'kind'],
        currencies.map((c) => [c.code, c.name_zh, c.name_en, c.kind]),
      ),
    ],
    [
      'appendix/banks.csv',
      csv(
        ['country', 'bank_code', 'bank_name'],
        banks.map((b) => [b.country, b.bank_code, b.bank_name]),
      ),
    ],
    ['nodejs/appendix/catalog.json', catalog],
    ['java/appendix/catalog.json', catalog],
    ['php/appendix/catalog.php', php],
    ['python/appendix/catalog.py', python],
    ['go/appendix/catalog.go', go],
  ]);
}

export function checkFiles() {
  for (const [path, content] of buildFiles())
    assert.equal(
      readFileSync(resolve(root, path), 'utf8'),
      content,
      `${path} 未同步，请运行 node _tooling/reference-codes.mjs`,
    );
}

export function verifySources(sourceRoot) {
  for (const file of reference.source.files) {
    const digest = createHash('sha256')
      .update(readFileSync(resolve(sourceRoot, file.path)))
      .digest('hex');
    assert.equal(digest, file.sha256, `源文件已变化，需核对代码表：${file.path}`);
  }
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const args = process.argv.slice(2);
  if (args.length === 0) {
    for (const [path, content] of buildFiles()) writeFileSync(resolve(root, path), content);
    console.log('参考代码表及五语言附录已生成');
  } else if (args.length === 1 && args[0] === '--check') {
    checkFiles();
    console.log('参考代码表校验通过');
  } else if (args.length === 2 && args[0] === '--verify-source') {
    validateReference(reference);
    verifySources(args[1]);
    console.log('源文件指纹校验通过');
  } else {
    throw new Error('用法：node _tooling/reference-codes.mjs [--check | --verify-source <project-p路径>]');
  }
}
