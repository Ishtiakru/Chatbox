import test from 'node:test';
import assert from 'node:assert/strict';
import { createIncomingCallFilter, SIGNAL_LIFETIME_MS } from '../src/incoming-call-filter.js';

function scheduler() {
  let now = 1_000_000, next = 1;
  const timers = new Map();
  return {
    now: () => now,
    setTimer(fn, delay) { const id = next++; timers.set(id, {fn, when: now + delay}); return id; },
    clearTimer(id) { timers.delete(id); },
    advance(ms) {
      const target = now + ms;
      for (;;) {
        const due = [...timers.entries()].filter(([, item]) => item.when <= target).sort((a,b) => a[1].when-b[1].when)[0];
        if (!due) break;
        timers.delete(due[0]); now = due[1].when; due[1].fn();
      }
      now = target;
    },
    get timerCount() { return timers.size; },
  };
}
function doc(id, created, overrides = {}, pending = false) {
  return { id, metadata: {hasPendingWrites: pending}, data: () => ({ receiverId:'bob', status:'ringing', createdAt:{toMillis:()=>created}, ...overrides }) };
}
test('incoming ringing calls expire without any new Firestore snapshot', () => {
  const clock = scheduler(), emitted = [];
  const filter = createIncomingCallFilter({ uid:'bob', onCalls:calls=>emitted.push(calls), ...clock });
  const created = clock.now();
  filter.update([doc('real-id', created, {id:'untrusted'}), doc('wrong-person', created, {receiverId:'alice'}), doc('ended', created, {status:'ended'}), doc('pending', created, {}, true)]);
  assert.deepEqual(emitted.at(-1).map(item=>item.id), ['real-id']);
  clock.advance(SIGNAL_LIFETIME_MS - 1);
  assert.deepEqual(emitted.at(-1).map(item=>item.id), ['real-id']);
  clock.advance(1);
  assert.deepEqual(emitted.at(-1), []);
  assert.equal(clock.timerCount, 0);
  filter.stop();
});
test('future clock-skew boundary, replaced snapshots, and unsubscribe cancel scheduled callbacks', () => {
  const clock = scheduler(), emitted = [];
  const filter = createIncomingCallFilter({ uid:'bob', onCalls:calls=>emitted.push(calls), ...clock });
  filter.update([doc('future', clock.now() + 61_000)]);
  assert.deepEqual(emitted.at(-1), []);
  clock.advance(1000);
  assert.deepEqual(emitted.at(-1).map(item=>item.id), ['future']);
  filter.update([]);
  assert.deepEqual(emitted.at(-1), []);
  assert.equal(clock.timerCount, 0);
  filter.update([doc('later', clock.now())]);
  filter.stop();
  const count = emitted.length;
  clock.advance(SIGNAL_LIFETIME_MS + 1);
  filter.update([doc('ignored', clock.now())]);
  assert.equal(emitted.length, count);
  assert.equal(clock.timerCount, 0);
});
