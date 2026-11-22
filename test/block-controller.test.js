import test from 'node:test';
import assert from 'node:assert/strict';
import { createBlockController } from '../src/block-controller.js';
const deferred = () => {let resolve,reject; const promise=new Promise((a,b)=>{resolve=a;reject=b;}); return {promise,resolve,reject};};
function fixture() {
  const listeners=[],ops=[],changes=[];
  const controller=createBlockController({
    subscribe:(uid,next,fail)=>{const entry={uid,next,fail,stopped:false};listeners.push(entry);return ()=>{entry.stopped=true;};},
    block:(uid,peer,conversation)=>{const entry={uid,peer,conversation,...deferred()};ops.push(entry);return entry.promise;},
    unblock:(uid,peer)=>{const entry={uid,peer,...deferred()};ops.push(entry);return entry.promise;},
    onChange:state=>changes.push(state),
  });
  controller.setUser('alice');
  return {controller,listeners,ops,changes};
}
const server=ids=>({ids,fromCache:false,pendingWrites:false});
const cache=ids=>({ids,fromCache:true,pendingWrites:false});

test('cache and pending snapshots cannot establish blocking state', () => {
  const f=fixture();
  f.listeners[0].next(cache(['bob']));
  assert.equal(f.controller.getState().status,'loading');
  assert.deepEqual(f.controller.getState().ids,[]);
  f.listeners[0].next({ids:['bob'],fromCache:false,pendingWrites:true});
  assert.equal(f.controller.getState().status,'loading');
  f.listeners[0].next(server(['bob']));
  assert.equal(f.controller.getState().status,'ready');
  assert.deepEqual(f.controller.getState().ids,['bob']);
  f.listeners[0].next(cache(['bob']));
  assert.equal(f.controller.getState().status,'loading');
  f.controller.dispose();
});

test('block is not confirmed before both write acknowledgement and matching server snapshot', async () => {
  const f=fixture(); f.listeners[0].next(server([]));
  const conversation={id:'chat'};
  const task=f.controller.block('bob',conversation);
  assert.equal(f.controller.getState().status,'saving');
  await assert.rejects(f.controller.block('eve',conversation));
  f.listeners[0].next({ids:['bob'],fromCache:false,pendingWrites:true});
  f.listeners[0].next(server(['bob']));
  assert.equal(f.controller.getState().status,'saving');
  f.ops[0].resolve(); await task;
  assert.equal(f.controller.getState().status,'ready');
  assert.deepEqual(f.controller.getState().ids,['bob']);
  assert.deepEqual(f.ops[0].conversation,conversation);
  f.controller.dispose();
});

test('acknowledgement without matching server snapshot stays unconfirmed', async () => {
  const f=fixture(); f.listeners[0].next(server([]));
  const task=f.controller.block('bob',{id:'chat'}); f.ops[0].resolve(); await task;
  assert.equal(f.controller.getState().status,'saving');
  f.listeners[0].next(cache(['bob']));
  assert.equal(f.controller.getState().status,'saving');
  f.listeners[0].next(server(['bob']));
  assert.equal(f.controller.getState().status,'ready');
  f.controller.dispose();
});

test('unblock stays unconfirmed until acknowledgement and matching snapshot; errors are redacted', async () => {
  const f=fixture(); f.listeners[0].next(server(['bob']));
  const task=f.controller.unblock('bob');
  f.listeners[0].next(server([]));
  assert.equal(f.controller.getState().status,'saving');
  f.ops[0].reject(new Error('private details')); await task;
  assert.equal(f.controller.getState().status,'error');
  assert.doesNotMatch(f.controller.getState().error,/private details/);
  f.controller.dispose();
});

test('account switch and disposal suppress late results and release subscriptions', async () => {
  const f=fixture();f.listeners[0].next(server([]));
  const task=f.controller.block('bob',{id:'old'});
  f.controller.setUser('carol');
  assert.equal(f.listeners[0].stopped,true);
  f.listeners[1].next(server(['dave']));
  f.ops[0].resolve(); await task;
  f.listeners[0].next(server(['bob']));
  assert.deepEqual(f.controller.getState().ids,['dave']);
  const count=f.changes.length;
  f.controller.dispose(); f.listeners[1].next(server([]));
  assert.equal(f.listeners[1].stopped,true);
  assert.equal(f.changes.length,count);
});

test('terminal subscription failure prevents changes until resubscribed', async () => {
  const f=fixture();f.listeners[0].next(server([]));f.listeners[0].fail(new Error('private'));
  assert.equal(f.controller.getState().status,'error');
  await assert.rejects(f.controller.block('bob',{id:'chat'}));
  f.controller.setUser('alice');f.listeners[1].next(server([]));
  assert.equal(f.controller.getState().status,'ready');
  f.controller.dispose();
});
