import test from 'node:test';
import assert from 'node:assert/strict';
import { createSlowOperationNotice } from '../src/slow-operation-notice.js';

function fixture() {
  const callbacks = new Map(), canceled = [], changes = [];
  let id = 0;
  const notice = createSlowOperationNotice(value => changes.push(value), {
    delay:8000,
    schedule:(callback,delay)=>{ assert.equal(delay,8000); callbacks.set(++id, callback); return id; },
    cancel:handle=>canceled.push(handle),
  });
  return {notice,callbacks,canceled,changes};
}

test('a fast operation completes without showing a notice', () => {
  const f=fixture(); const finish=f.notice.begin(); finish();
  assert.deepEqual(f.canceled,[1]); assert.deepEqual(f.changes,[]);
  f.callbacks.get(1)(); // A callback already queued by the runtime is stale.
  assert.deepEqual(f.changes,[]);
});

test('a slow operation shows a notice until acknowledgement or rejection', () => {
  const f=fixture(); const finish=f.notice.begin();
  f.callbacks.get(1)(); assert.deepEqual(f.changes,[true]);
  finish(); finish(); assert.deepEqual(f.changes,[true,false]);
});

test('reset prevents a stale completion or timer affecting the next account', () => {
  const f=fixture(); const oldFinish=f.notice.begin();
  f.notice.reset(); const newFinish=f.notice.begin();
  oldFinish(); f.callbacks.get(1)(); assert.deepEqual(f.changes,[]);
  f.callbacks.get(2)(); assert.deepEqual(f.changes,[true]);
  oldFinish(); assert.deepEqual(f.changes,[true]);
  newFinish(); assert.deepEqual(f.changes,[true,false]);
});

test('starting a replacement invalidates an already queued older timer', () => {
  const f=fixture(); const oldFinish=f.notice.begin(); const finish=f.notice.begin();
  f.callbacks.get(1)(); oldFinish(); assert.deepEqual(f.changes,[]);
  f.callbacks.get(2)(); finish(); assert.deepEqual(f.changes,[true,false]);
});
