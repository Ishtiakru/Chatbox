import test from 'node:test';
import assert from 'node:assert/strict';
import { copyCodeRequest } from '../src/code-request-handoff.js';

test('copies a multilingual code request only upon explicit invocation', async () => {
  const calls = [];
  const copied = await copyCodeRequest('  কলের রং পাল্টাও 🌈  ', {writeText: text => calls.push(text)});
  assert.equal(copied, true);
  assert.equal(calls.length, 1);
  assert.match(calls[0], /Do not deploy without review/);
  assert.ok(calls[0].endsWith('কলের রং পাল্টাও 🌈'));
});

test('rejects blank, oversized, malformed and unavailable clipboard requests', async () => {
  let calls = 0;
  const clipboard = {writeText: () => { calls++; }};
  for (const request of [null, '', '   ', 'x'.repeat(1001)]) assert.equal(await copyCodeRequest(request, clipboard), false);
  assert.equal(calls, 0);
  assert.equal(await copyCodeRequest('hello', null), false);
  assert.equal(await copyCodeRequest('hello', {writeText: () => Promise.reject(new Error('denied'))}), false);
  assert.equal(await copyCodeRequest('hello', {writeText: () => {throw new Error('denied');}}), false);
});
