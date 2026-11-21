import test from 'node:test';
import assert from 'node:assert/strict';
import { sairaReply, escapeHtml, APP_VERSION } from '../src/knowledge.js';

test('guide is honest about unavailable services', () => {
  assert.match(sairaReply('video call'), /কাজ করে না/);
  assert.match(sairaReply('admin'), /লক/);
  assert.match(sairaReply('update'), new RegExp(APP_VERSION.replaceAll('.', '\\.')));
  assert.match(sairaReply('something unexpected'), /অফলাইন/);
});
test('user supplied text is escaped before rendering', () => {
  assert.equal(escapeHtml('<img src=x onerror="alert(1)">'), '&lt;img src=x onerror=&quot;alert(1)&quot;&gt;');
});
