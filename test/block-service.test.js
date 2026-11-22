import test from 'node:test';
import assert from 'node:assert/strict';
import { blockAndClose, unblock, watchOwnBlocks } from '../src/block-service.js';

const db={};
const valid={id:'chat',participants:['alice','bob'],status:'active',pendingWrites:false};
test('blocking requires the exact other participant and a stable conversation', () => {
  for (const [uid,peer,conversation] of [
    ['alice','alice',valid], ['alice','eve',valid], ['alice','bob',{...valid,id:'bad/path'}],
    ['alice','bob',{...valid,status:'unknown'}], ['alice','bob',{...valid,pendingWrites:true}],
    ['alice','bob',{...valid,participants:['alice','bob','eve']}], ['bad/path','bob',valid],
  ]) assert.throws(()=>blockAndClose(db,uid,peer,conversation));
});
test('block-list APIs reject malformed identities before using Firestore', () => {
  assert.throws(()=>unblock(db,'alice','alice'));
  assert.throws(()=>unblock(db,'alice','bad/path'));
  assert.throws(()=>watchOwnBlocks(db,'bad/path',()=>{},()=>{}));
  assert.throws(()=>watchOwnBlocks(db,'alice',null,()=>{}));
});
