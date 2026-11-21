import test from 'node:test';
import assert from 'node:assert/strict';
import {copyChatboxId} from '../src/copy-chatbox-id.js';
import {liveIdCard, liveIdFeedback} from '../src/live-id-view.js';

test('copy writes only the supplied ID and reports actual completion', async () => {
  let written; let resolve;
  const result = copyChatboxId('user-ID-123', {writeText(value) { written = value; return new Promise(r => { resolve = r; }); }});
  assert.equal(written, 'user-ID-123');
  resolve();
  assert.equal(await result, true);
});

test('copy failure or missing clipboard offers manual selection without claiming success', async () => {
  assert.equal(await copyChatboxId('id', undefined), false);
  assert.equal(await copyChatboxId('id', {writeText() { throw new Error('denied'); }}), false);
  assert.equal(await copyChatboxId('id', {writeText() { return Promise.reject(new Error('denied')); }}), false);
  let called = false;
  assert.equal(await copyChatboxId('', {writeText() { called = true; }}), false);
  assert.equal(await copyChatboxId('x'.repeat(129), {writeText() { called = true; }}), false);
  assert.equal(called, false);
  assert.match(liveIdFeedback('unavailable'), /Press and hold/);
  assert.doesNotMatch(liveIdFeedback('unavailable'), /Copied/);
});

test('ID card escapes data and gives accessible status and copy action', () => {
  const card = liveIdCard('<img src=x>', 'copied');
  assert.match(card, /&lt;img src=x&gt;/);
  assert.doesNotMatch(card, /<img/);
  assert.match(card, /data-action="live-copy-id"/);
  assert.match(card, /role="status"/);
  assert.match(card, /Copied/);
  assert.match(card, /clipboard/);
  assert.equal(liveIdFeedback('unknown'), '');
});
