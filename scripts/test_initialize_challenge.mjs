import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import http from 'node:http';
import { once } from 'node:events';
import { readDescriptor, discover, initializeChallenge } from './initialize_challenge.mjs';

async function mock(t, root, directory, id) {
  const token = 'a'.repeat(64);
  let calls = [];
  const server = http.createServer(async (req, res) => {
    assert.equal(req.headers.authorization, `Bearer ${token}`);
    res.setHeader('Content-Type', 'application/json');
    if (req.url === '/status') return res.end(JSON.stringify({ version: 1, projects: [root] }));
    assert.equal(req.url, '/initialize-challenge');
    assert.equal(req.method, 'POST');
    let body = ''; for await (const chunk of req) body += chunk;
    calls.push(JSON.parse(body));
    res.end(JSON.stringify({ status: 'created', associated: true }));
  });
  server.listen(0, '127.0.0.1'); await once(server, 'listening');
  t.after(() => { server.closeAllConnections(); server.close(); });
  const descriptor = path.join(directory, `${id}.json`);
  fs.writeFileSync(descriptor, JSON.stringify({ version: 1, pid: id, port: server.address().port, token }), { mode: 0o600 });
  return { descriptor, calls };
}

test('客户端：只调用指定项目，Java/Go 均支持，不输出 token', async t => {
  const temp = fs.realpathSync(fs.mkdtempSync(path.join(os.tmpdir(), 'challenge-client-test-')));
  t.after(() => fs.rmSync(temp, { recursive: true, force: true }));
  const m = await mock(t, temp, temp, 1);
  for (const language of ['java', 'go']) {
    assert.equal((await initializeChallenge(temp, 'two-sum', language, temp)).status, 'created');
  }
  assert.deepEqual(m.calls, ['java', 'go'].map(language => ({ project: temp, slug: 'two-sum', language })));
  await assert.rejects(initializeChallenge(temp, '../bad', 'go', temp), /合法 slug/);
  await assert.rejects(initializeChallenge(temp, 'two-sum', 'python', temp), /java 或 go/);
  assert.equal(m.calls.length, 2);
  assert.equal((await discover('/not-open', temp)).length, 0);
  await mock(t, temp, temp, 2);
  await assert.rejects(initializeChallenge(temp, 'two-sum', 'go', temp), /多个 IDEA/);
  assert.equal(m.calls.length, 2);
});

test('客户端：显式国际站不能落回中国站，默认请求保持旧协议兼容', async t => {
  const temp = fs.realpathSync(fs.mkdtempSync(path.join(os.tmpdir(), 'challenge-site-test-')));
  t.after(() => fs.rmSync(temp, { recursive: true, force: true }));
  const m = await mock(t, temp, temp, 1);
  await initializeChallenge(temp, 'two-sum', 'java', temp, 'leetcode');
  await initializeChallenge(temp, 'two-sum', 'go', temp, 'leetcodecn');
  assert.deepEqual(m.calls, [
    { project: temp, slug: 'two-sum', language: 'java', dojo: 'leetcode' },
    { project: temp, slug: 'two-sum', language: 'go' },
  ]);
  await assert.rejects(initializeChallenge(temp, 'two-sum', 'java', temp, 'other'));
  assert.equal(m.calls.length, 2);
});

test('客户端：拒绝不安全描述文件；无桥接时不降级写数据库', async t => {
  const temp = fs.mkdtempSync(path.join(os.tmpdir(), 'challenge-descriptor-test-'));
  t.after(() => fs.rmSync(temp, { recursive: true, force: true }));
  const file = path.join(temp, '1.json');
  fs.writeFileSync(file, JSON.stringify({ version: 1, pid: 1, port: 1, token: 'a'.repeat(64) }), { mode: 0o644 });
  assert.throws(() => readDescriptor(file), /权限/);
  fs.chmodSync(file, 0o600);
  fs.symlinkSync(file, path.join(temp, '2.json'));
  assert.throws(() => readDescriptor(path.join(temp, '2.json')), /权限/);
  assert.deepEqual(await discover(temp, path.join(temp, 'missing')), []);
  await assert.rejects(initializeChallenge(temp, 'two-sum', 'java', path.join(temp, 'missing')), /未找到/);
});
