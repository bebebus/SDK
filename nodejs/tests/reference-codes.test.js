import assert from 'node:assert/strict';
import { test } from 'node:test';
import { checkFiles, csv, reference, validateReference } from '../../_tooling/reference-codes.mjs';

test('参考代码表与 Markdown、CSV、五语言字典保持一致', () => {
  checkFiles();
});

test('区域扩展与统一银行代码保持语义', () => {
  assert.deepEqual(reference.countries.find((c) => c.code === 'HK').currencies, ['HKD']);
  assert.equal(reference.currencies.find((c) => c.code === 'USDT').kind, 'crypto');
  const banks = (country) => reference.banks.filter((b) => b.country === country).map((b) => b.bank_code);
  assert.ok(banks('PH').includes('MAYA'));
  assert.ok(banks('PH').includes('MAYABANK'));
  assert.ok(banks('PH').includes('MAYABUSINESS'));
  assert.ok(banks('HK').includes('HSBC'));
  assert.ok(banks('VN').includes('VIETCOMBANK'));
  assert.ok(!banks('HK').includes('FPS'));
  assert.ok(!banks('HK').includes('004'));
});

test('同国重复银行代码拒绝，跨国家同码允许', () => {
  const data = structuredClone(reference);
  data.banks.push({ ...data.banks[0] });
  assert.throws(() => validateReference(data), /代码不能重复/);
  assert.ok(reference.banks.filter((b) => b.bank_code === 'HSBC').length > 1);
  validateReference(reference);
});

test('未知国家、币种和来源拒绝', () => {
  for (const mutate of [
    (d) => {
      d.banks[0].country = 'ZZ';
    },
    (d) => {
      d.countries[0].currencies = ['ZZZ'];
    },
    (d) => {
      d.banks[0].source = 'missing';
    },
  ]) {
    const data = structuredClone(reference);
    mutate(data);
    assert.throws(() => validateReference(data));
  }
});

test('禁止输出上游私有码、清算码或加入支付方式表', () => {
  for (const field of ['upstream', 'clearing', 'credentials']) {
    const data = structuredClone(reference);
    data.banks[0][field] = 'private';
    assert.throws(() => validateReference(data));
  }
  const data = structuredClone(reference);
  data.payment_methods = [];
  assert.throws(() => validateReference(data));
});

test('CSV 保留 Unicode、转义逗号双引号换行并防止公式注入', () => {
  const value = csv(
    ['code', 'name'],
    [
      ['VN', '银行,"分行"\n地址'],
      ['PH', '=1+1'],
    ],
  );
  assert.ok(value.startsWith('\uFEFF'));
  assert.ok(value.includes('"银行,""分行""\n地址"'));
  assert.ok(value.includes('"\'=1+1"'));
  assert.ok(value.endsWith('\r\n'));
});

test('越南公开目录包含通道子集以外的机构，保留外部代码原文', () => {
  const directory = reference.bank_directories.find((d) => d.country === 'VN' && d.scheme === 'vietqr');
  const byCode = new Map(directory.entries.map((entry) => [entry.reference_code, entry]));
  for (const code of ['PVCB', 'HSBC', 'CIMB', 'NHB HN', 'Ubank', 'TIMO', 'IBK - HN', 'KBHN']) {
    assert.ok(byCode.has(code), `缺少 ${code}`);
  }
  assert.equal(byCode.get('HSBC').platform_bank_code, null);
  assert.equal(byCode.get('CIMB').platform_bank_code, null);
  assert.equal(byCode.get('VCB').platform_bank_code, 'VIETCOMBANK');
  assert.equal(byCode.get('Vikki').platform_bank_code, 'DONGABANK');
  assert.equal(byCode.get('MBV').platform_bank_code, 'OCEANBANK');
  assert.equal(byCode.get('VCB').bin, '970436');
  assert.equal(reference.banks.filter((bank) => bank.country === 'VN').length, 44);
  assert.ok(directory.entries.length > 44);
});

test('公开目录不接受跨国家银行码、重复 BIN 或附带上游映射', () => {
  for (const mutate of [
    (d) => {
      d.bank_directories[0].entries[0].platform_bank_code = 'HSBC';
    },
    (d) => {
      d.bank_directories[0].entries[1].bin = d.bank_directories[0].entries[0].bin;
    },
    (d) => {
      d.bank_directories[0].entries[0].upstream = 'private';
    },
  ]) {
    const data = structuredClone(reference);
    mutate(data);
    assert.throws(() => validateReference(data));
  }
});

test('公开目录未登记平台码在 CSV 中输出空值', () => {
  assert.ok(csv(['platform_bank_code'], [[null]]).endsWith('""\r\n'));
});
