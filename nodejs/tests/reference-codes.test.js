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
