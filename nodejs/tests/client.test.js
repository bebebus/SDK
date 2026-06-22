// 单测：客户端请求构建与信封解析（用本地 node:http 桩服务器，不依赖外网）。
import { test } from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';

import { Client } from '../src/client.js';
import { Config, Environment } from '../src/config.js';
import { ApiError, TransportError } from '../src/errors.js';
import { sign } from '../src/signer.js';

const SECRET_PAY = 'sk_pay_0123456789abcdef0123456789abcdef';
const SECRET_PAYOUT = 'sk_payout_0123456789abcdef0123456789abc';

// 启动一个一次性桩服务器，回调收到的 (path, body)，并返回指定信封。
function startStub(handler) {
  return new Promise((resolveServer) => {
    const server = http.createServer((req, res) => {
      const chunks = [];
      req.on('data', (c) => chunks.push(c));
      req.on('end', () => {
        const body = JSON.parse(Buffer.concat(chunks).toString('utf8'));
        const envelope = handler(req.url, body, res);
        if (res.writableEnded) return;
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify(envelope ?? { code: 0, message: 'ok', data: {} }));
      });
    });
    server.listen(0, '127.0.0.1', () => {
      const { port } = server.address();
      resolveServer({ server, baseUrl: `http://127.0.0.1:${port}/api/open/v1` });
    });
  });
}

function makeClient(baseUrl) {
  return new Client(
    new Config({
      merchantNo: 'M00000001',
      apiKey: 'ak_demo_key',
      apiSecretPay: SECRET_PAY,
      apiSecretPayout: SECRET_PAYOUT,
      baseUrl,
    }),
  );
}

test('Environment 预设基址正确', () => {
  assert.equal(Environment.PRODUCTION, 'https://api.project-p-merchant.cniia.cloud/api/open/v1');
  assert.equal(Environment.SANDBOX, 'http://127.0.0.1:3090/api/open/v1');
});

test('Config baseUrl 覆盖优先于 environment 且去除末尾斜杠', () => {
  const c = new Config({
    merchantNo: 'M',
    apiKey: 'k',
    environment: Environment.PRODUCTION,
    baseUrl: 'https://api.custom.example/api/open/v1/',
  });
  assert.equal(c.baseUrl, 'https://api.custom.example/api/open/v1');
});

test('payCreate：注入通用字段/timestamp/nonce，过滤 null，签名用 pay 密钥，命中正确 path', async () => {
  let captured = null;
  const { server, baseUrl } = await startStub((url, body) => {
    captured = { url, body };
    return { code: 0, message: 'ok', data: { order_no: 'P1', status: 'pending' } };
  });
  try {
    const client = makeClient(baseUrl);
    const { data, raw } = await client.payCreate({
      out_order_no: '202501010001',
      amount: 10000,
      currency: 'PHP',
      pay_method: 'gcash',
      country: 'PH',
      notify_url: 'https://merchant.example.com/api/notify/pay',
      return_url: null, // 应被过滤
    });

    assert.equal(captured.url, '/api/open/v1/merchant/pay/create');
    // 通用字段注入
    assert.equal(captured.body.merchant_no, 'M00000001');
    assert.equal(captured.body.api_key, 'ak_demo_key');
    assert.equal(typeof captured.body.timestamp, 'number');
    assert.equal(typeof captured.body.nonce, 'string');
    assert.ok(captured.body.nonce.length > 0);
    // null 字段被过滤
    assert.ok(!('return_url' in captured.body));
    // 签名用 pay 密钥，且对收到的（去 sign）体可复现
    const { sign: gotSign, ...rest } = captured.body;
    assert.equal(gotSign, sign(rest, SECRET_PAY));
    // 信封解析
    assert.equal(data.order_no, 'P1');
    assert.equal(raw.code, 0);
  } finally {
    server.close();
  }
});

test('每请求 nonce 唯一', async () => {
  const seen = [];
  const { server, baseUrl } = await startStub((url, body) => {
    seen.push(body.nonce);
    return { code: 0, message: 'ok', data: {} };
  });
  try {
    const client = makeClient(baseUrl);
    await client.balanceQuery({});
    await client.balanceQuery({});
    assert.equal(seen.length, 2);
    assert.notEqual(seen[0], seen[1]);
  } finally {
    server.close();
  }
});

test('payoutCreate：签名用 payout 密钥', async () => {
  let captured = null;
  const { server, baseUrl } = await startStub((url, body) => {
    captured = body;
    return { code: 0, message: 'ok', data: { payout_no: 'W1', status: 'pending' } };
  });
  try {
    const client = makeClient(baseUrl);
    await client.payoutCreate({
      out_payout_no: 'WD1',
      amount: 100000,
      currency: 'PHP',
      pay_method: 'bank',
      country: 'PH',
      notify_url: 'https://m.example.com/cb',
      account_no: '1234567890',
      account_name: 'San Zhang',
      bank_code: 'BDO',
    });
    const { sign: gotSign, ...rest } = captured;
    assert.equal(gotSign, sign(rest, SECRET_PAYOUT));
    // 用 pay 密钥应当对不上
    assert.notEqual(gotSign, sign(rest, SECRET_PAY));
  } finally {
    server.close();
  }
});

test('payoutReceiptQuery：inline 以整数 1/0 发送', async () => {
  const bodies = [];
  const { server, baseUrl } = await startStub((url, body) => {
    bodies.push(body);
    return { code: 0, message: 'ok', data: {} };
  });
  try {
    const client = makeClient(baseUrl);
    await client.payoutReceiptQuery({ payout_no: 'W1', inline: true });
    await client.payoutReceiptQuery({ payout_no: 'W1', inline: false });
    await client.payoutReceiptQuery({ payout_no: 'W1' }); // 省略
    assert.equal(bodies[0].inline, 1);
    assert.strictEqual(bodies[0].inline === true, false); // 不是布尔
    assert.equal(bodies[1].inline, 0);
    assert.ok(!('inline' in bodies[2]));
  } finally {
    server.close();
  }
});

test('code !== 0 抛 ApiError，携带 code/message/data/raw', async () => {
  const { server, baseUrl } = await startStub(() => ({
    code: 100104,
    message: '签名错误',
    data: { missing_fields: ['x'] },
  }));
  try {
    const client = makeClient(baseUrl);
    await assert.rejects(
      () => client.payQuery({ out_order_no: 'x' }),
      (err) => {
        assert.ok(err instanceof ApiError);
        assert.equal(err.code, 100104);
        assert.equal(err.message, '签名错误');
        assert.deepEqual(err.data, { missing_fields: ['x'] });
        assert.equal(err.raw.code, 100104);
        return true;
      },
    );
  } finally {
    server.close();
  }
});

test('HTTP 非 2xx 抛 TransportError', async () => {
  const { server, baseUrl } = await startStub((url, body, res) => {
    res.writeHead(500, { 'Content-Type': 'text/plain' });
    res.end('boom');
  });
  try {
    const client = makeClient(baseUrl);
    await assert.rejects(
      () => client.balanceQuery({}),
      (err) => {
        assert.ok(err instanceof TransportError);
        assert.equal(err.statusCode, 500);
        assert.equal(err.body, 'boom');
        return true;
      },
    );
  } finally {
    server.close();
  }
});

test('响应非合法 JSON 抛 TransportError', async () => {
  const { server, baseUrl } = await startStub((url, body, res) => {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end('not-json');
  });
  try {
    const client = makeClient(baseUrl);
    await assert.rejects(() => client.payMethodsQuery({}), TransportError);
  } finally {
    server.close();
  }
});

test('全部 11 个端点方法均存在', () => {
  const client = makeClient('http://127.0.0.1:1/api/open/v1');
  const methods = [
    'payCreate',
    'payQuery',
    'payMethodsQuery',
    'balanceQuery',
    'payTestComplete',
    'payoutCreate',
    'payoutQuery',
    'payoutBanksQuery',
    'payoutProofQuery',
    'payoutReceiptQuery',
    'payoutTestComplete',
  ];
  for (const m of methods) {
    assert.equal(typeof client[m], 'function', `missing method ${m}`);
  }
});

test('调用 pay 类端点但未配 apiSecretPay 抛 TypeError', async () => {
  const client = new Client(
    new Config({ merchantNo: 'M', apiKey: 'k', apiSecretPayout: SECRET_PAYOUT, baseUrl: 'http://127.0.0.1:1/x' }),
  );
  await assert.rejects(() => client.payCreate({ out_order_no: 'x', amount: 1 }), TypeError);
});
