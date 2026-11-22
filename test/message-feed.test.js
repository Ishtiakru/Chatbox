import test from 'node:test';
import assert from 'node:assert/strict';
import { createMessageFeed } from '../src/message-feed.js';

function doc(id, seconds, pending = false) {
  return { id, metadata:{hasPendingWrites:pending}, data:()=>({body:id,sentAt:seconds == null ? null : {seconds,nanoseconds:0}}) };
}
function fixture(fetchOlder = async () => ({docs:[]}), pageSize = 1) {
  let recent, fail, unsubscribed = 0;
  const changes = [], errors = [];
  const feed = createMessageFeed({
    listenRecent:(next,error)=>{recent=next;fail=error;return ()=>{unsubscribed++;};},
    fetchOlder,pageSize,onChange:value=>changes.push(value),onError:error=>errors.push(error),
  });
  return {feed,changes,errors,recent:(docs,fromCache=false)=>recent({docs,metadata:{fromCache,hasPendingWrites:docs.some(d=>d.metadata.hasPendingWrites)}}),fail:e=>fail(e),get unsubscribed(){return unsubscribed;}};
}
test('realtime window changes retain displaced messages and older pages merge in order', async () => {
  let used;
  const f=fixture(async cursor=>{used=cursor.id;return {docs:[doc('one',1)]};},2);
  f.recent([doc('three',3),doc('four',4)]);
  f.recent([doc('four',4),doc('five',5)]);
  await f.feed.loadOlder();
  assert.equal(used,'three');
  assert.deepEqual(f.changes.at(-1).messages.map(m=>m.id),['one','three','four','five']);
  assert.equal(f.changes.at(-1).hasMore,false);
  f.feed.stop(); assert.equal(f.unsubscribed,1);
});
test('cached fragments never set the pagination anchor or survive initial server confirmation', async () => {
  let used;
  const f=fixture(async cursor=>{used=cursor.id;return {docs:[]};});
  f.recent([doc('stale',1)],true);
  assert.equal(f.changes.at(-1).fromCache,true);
  assert.equal(f.changes.at(-1).hasMore,false);
  await f.feed.loadOlder(); assert.equal(used,undefined);
  f.recent([doc('new',10)]);
  assert.deepEqual(f.changes.at(-1).messages.map(m=>m.id),['new']);
  await f.feed.loadOlder(); assert.equal(used,'new');
  f.feed.stop();
});
test('pending messages never become cursors and update without duplicates', async () => {
  const f=fixture();
  f.recent([doc('pending',null,true)]);
  assert.equal(f.changes.at(-1).hasMore,false);
  f.recent([doc('pending',2,false)]);
  assert.equal(f.changes.at(-1).hasMore,true);
  assert.equal(f.changes.at(-1).messages.length,1);
  f.feed.stop();
});
test('errors allow retry; stopping prevents late results leaking across conversations', async () => {
  let resolve, attempt=0;
  const f=fixture(()=>{attempt++; if(attempt===1) return Promise.reject(new Error('offline')); return new Promise(r=>{resolve=r;});});
  f.recent([doc('last',5)]);
  await f.feed.loadOlder();
  assert.equal(f.errors.length,1); assert.equal(f.changes.at(-1).loading,false); assert.equal(f.changes.at(-1).hasMore,true);
  const pending=f.feed.loadOlder();
  assert.equal(f.changes.at(-1).loading,true);
  const count=f.changes.length;
  f.feed.stop(); f.feed.stop(); resolve({docs:[doc('old',1)]}); await pending;
  f.recent([doc('leak',6)]); f.fail(new Error('late'));
  assert.equal(f.unsubscribed,1); assert.equal(f.changes.length,count); assert.equal(f.errors.length,1);
});
test('a second load during a pending request is ignored', async () => {
  let resolve, count=0;
  const f=fixture(()=>{count++; return new Promise(r=>{resolve=r;});});
  f.recent([doc('last',5)]);
  const first=f.feed.loadOlder(); await f.feed.loadOlder();
  assert.equal(count,1); resolve({docs:[]}); await first; f.feed.stop();
});
test('server-confirmed short windows have no older page; cached windows remain retryable', () => {
  let next;
  const changes=[];
  const f=createMessageFeed({listenRecent:n=>{next=n;return ()=>{};},fetchOlder:async()=>({docs:[]}),onChange:s=>changes.push(s),onError:()=>{}});
  next({docs:[doc('one',1)],metadata:{fromCache:true,hasPendingWrites:false}});
  assert.equal(changes.at(-1).hasMore,false);
  next({docs:[doc('one',1)],metadata:{fromCache:false,hasPendingWrites:false}});
  assert.equal(changes.at(-1).hasMore,false);
  f.stop();
});
test('same-timestamp message IDs sort by UTF-8 order', () => {
  const f=fixture();
  f.recent([doc('\u{10000}',1),doc('\ue000',1)]);
  assert.deepEqual(f.changes.at(-1).messages.map(m=>m.id),['\ue000','\u{10000}']);
  f.feed.stop();
});

test('a rejected optimistic message is removed instead of retained as delivered', () => {
  const f = fixture();
  f.recent([doc('old',1), doc('rejected',null,true)]);
  assert.deepEqual(f.changes.at(-1).messages.map(m=>m.id), ['old','rejected']);
  f.recent([doc('old',1)]);
  assert.deepEqual(f.changes.at(-1).messages.map(m=>m.id), ['old']);
  f.feed.stop();
});

test('an acknowledged message remains when displaced from the live window', () => {
  const f = fixture(undefined,2);
  f.recent([doc('committed',1,true),doc('next',2)]);
  f.recent([doc('committed',1,false),doc('next',2)]);
  f.recent([doc('next',2),doc('newer',3)]);
  assert.deepEqual(f.changes.at(-1).messages.map(m=>m.id), ['committed','next','newer']);
  f.feed.stop();
});

test('an unacknowledged message displaced from the live window is not shown as delivered', () => {
  const f = fixture();
  f.recent([doc('pending',1,true)]);
  f.recent([doc('newer',2)]);
  assert.deepEqual(f.changes.at(-1).messages.map(m=>m.id), ['newer']);
  // If later observed as committed it can reappear normally.
  f.recent([doc('pending',1), doc('newer',2)]);
  assert.deepEqual(f.changes.at(-1).messages.map(m=>m.id), ['pending','newer']);
  f.feed.stop();
});

test('optimistic messages remain marked pending until acknowledged', () => {
  const f = fixture();
  f.recent([doc('pending',1,true)]);
  assert.equal(f.changes.at(-1).messages[0].pendingWrites,true);
  f.recent([doc('pending',1,false)]);
  assert.equal(f.changes.at(-1).messages[0].pendingWrites,false);
  f.feed.stop();
});

test('terminal listener errors and retryable older-page errors are separate', async () => {
  let fail;
  const transient=[], fatal=[];
  const feed=createMessageFeed({
    listenRecent:(next,error)=>{fail=error;next({docs:[doc('newest',10)],metadata:{fromCache:false,hasPendingWrites:false}});return ()=>{};},
    pageSize:1,fetchOlder:async()=>{throw new Error('page failed');},onChange:()=>{},
    onError:error=>transient.push(error.message),onFatalError:error=>fatal.push(error.message),
  });
  await feed.loadOlder();
  assert.deepEqual(transient,['page failed']);
  assert.deepEqual(fatal,[]);
  fail(new Error('listener failed'));
  assert.deepEqual(fatal,['listener failed']);
  feed.stop();
  fail(new Error('late'));
  assert.deepEqual(fatal,['listener failed']);
});


test('disconnected server windows reset the anchor so missing messages can be paged', async () => {
  let used;
  const f=fixture(async cursor=>{used=cursor.id;return {docs:[doc('middle',3)]};},2);
  f.recent([doc('old1',1),doc('old2',2)]);
  f.recent([doc('new1',4),doc('new2',5)]);
  assert.deepEqual(f.changes.at(-1).messages.map(m=>m.id),['new1','new2']);
  assert.equal(f.changes.at(-1).hasMore,true);
  await f.feed.loadOlder();
  assert.equal(used,'new1');
  assert.deepEqual(f.changes.at(-1).messages.map(m=>m.id),['middle','new1','new2']);
  f.feed.stop();
});

test('an in-flight page from an obsolete window is ignored after reset', async () => {
  let resolve;
  const f=fixture(()=>new Promise(r=>{resolve=r;}));
  f.recent([doc('old',1)]);
  const page=f.feed.loadOlder();
  f.recent([doc('new',10)]);
  assert.equal(f.changes.at(-1).loading,false);
  const count=f.changes.length;
  resolve({docs:[doc('stale',0)]}); await page;
  assert.equal(f.changes.length,count);
  assert.deepEqual(f.changes.at(-1).messages.map(m=>m.id),['new']);
  f.feed.stop();
});

test('cache fallback does not add disconnected historical fragments after server confirmation', () => {
  const f=fixture();
  f.recent([doc('server',10)]);
  f.recent([doc('stale',1),doc('pending',null,true)],true);
  assert.deepEqual(f.changes.at(-1).messages.map(m=>m.id),['server','pending']);
  assert.equal(f.changes.at(-1).fromCache,true);
  f.recent([doc('server',10)]);
  assert.deepEqual(f.changes.at(-1).messages.map(m=>m.id),['server']);
  assert.equal(f.changes.at(-1).fromCache,false);
  f.feed.stop();
});


test('an empty server query followed by a full window permits older paging', async () => {
  let used;
  const f=fixture(async cursor=>{used=cursor.id;return {docs:[doc('missing',1)]};},2);
  f.recent([]);
  assert.equal(f.changes.at(-1).hasMore,false);
  f.recent([doc('new1',2),doc('new2',3)]);
  assert.equal(f.changes.at(-1).hasMore,true);
  await f.feed.loadOlder(); assert.equal(used,'new1');
  assert.deepEqual(f.changes.at(-1).messages.map(m=>m.id),['missing','new1','new2']);
  f.feed.stop();
});
