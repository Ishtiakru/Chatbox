import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {hasBoundedJpegSignature,createConversationPhotoService} from '../src/conversation-photo.js';
import {isolatedPhotoRules} from '../scripts/generate-isolated-photo-rules.mjs';
const jpeg=()=>new Uint8Array([255,216,255,1,2,255,217]);
function fixture() {
  let current=true, snapshot=null; const calls=[];
  const api={doc:(db,...parts)=>parts.join('/'),serverTimestamp:()=>42,bytesFrom:bytes=>({toUint8Array:()=>bytes}),
    setDoc:async(ref,data)=>{calls.push({ref,data});},getDocFromServer:async()=>snapshot,deleteDoc:async ref=>{calls.push({ref,deleted:true});}};
  const guard={run:fn=>{if(!current)throw new Error('account changed');return fn();}};
  const service=createConversationPhotoService({db:{},guard,api});
  return {service,calls,api,stop:()=>{current=false;},snapshot:value=>{snapshot=value;}};
}
const snap=(owner,bytes,metadata={fromCache:false,hasPendingWrites:false})=>({metadata,exists:()=>true,data:()=>({ownerId:owner,jpeg:{toUint8Array:()=>bytes}})});
test('photo input bounds and JPEG signature are checked, not full decoding',()=>{
  assert.equal(hasBoundedJpegSignature(jpeg()),true);
  for(const value of [null,new Uint8Array(),new Uint8Array([255,216,255,1,217]),new Uint8Array(32769),[255,216,255,255,217]]) assert.equal(hasBoundedJpegSignature(value),false);
  const max=new Uint8Array(32768);max.set([255,216,255]);max.set([255,217],max.length-2);assert.equal(hasBoundedJpegSignature(max),true);
});
test('write and removal use guarded paths and copy bytes before dispatch',async()=>{
  const {service,calls,stop}=fixture(),data=jpeg();
  await service.put('chat','alice',data);
  data[3]=99;
  assert.equal(calls[0].ref,'conversations/chat/sharedPhotos/alice');
  assert.equal(calls[0].data.jpeg.toUint8Array()[3],1);
  assert.equal(calls[0].data.ownerId,'alice');
  await assert.rejects(service.put('chat','alice',new Uint8Array([1])),/Invalid photo/);
  await assert.rejects(service.put('../evil','alice',jpeg()),/Invalid photo path/);
  await service.remove('chat','alice');assert.equal(calls[1].deleted,true);
  stop();await assert.rejects(service.remove('chat','alice'),/account changed/);
});
test('reads accept only server-confirmed matching bounded bytes and recheck account',async()=>{
  const f=fixture(); f.snapshot(snap('alice',jpeg()));
  assert.deepEqual(await f.service.get('chat','alice'),jpeg());
  for(const value of [snap('bob',jpeg()),snap('alice',new Uint8Array([1])),snap('alice',jpeg(),{fromCache:true,hasPendingWrites:false}),snap('alice',jpeg(),{fromCache:false,hasPendingWrites:true})]) {
    f.snapshot(value);await assert.rejects(f.service.get('chat','alice'));
  }
  f.snapshot({metadata:{fromCache:false,hasPendingWrites:false},exists:()=>false});assert.equal(await f.service.get('chat','alice'),null);
  const original=f.api.getDocFromServer;
  f.api.getDocFromServer=async()=>{f.stop();return snap('alice',jpeg());};
  await assert.rejects(f.service.get('chat','alice'),/account changed/);
  f.api.getDocFromServer=original;
});
test('photo rules preserve restricted account base and reject template changes',()=>{
  const base=readFileSync('firestore.combined-experimental.rules','utf8');
  const rules=isolatedPhotoRules(base,'alice','bob');
  assert.match(rules,/match \/sharedPhotos\/\{ownerId\}/);
  assert.match(rules,/request.auth.uid in \['alice', 'bob'\]/);
  assert.doesNotMatch(base,/sharedPhotos/);
  assert.throws(()=>isolatedPhotoRules(base+'\n','alice','bob'));
  assert.throws(()=>isolatedPhotoRules(base,'alice','alice'));
});
