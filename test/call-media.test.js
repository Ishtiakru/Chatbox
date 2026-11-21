import test from 'node:test';
import assert from 'node:assert/strict';
import { readLocalMedia, setLocalMediaEnabled } from '../src/call-media.js';

const track = (kind, enabled=true, readyState='live') => ({kind,enabled,readyState});
const stream = (...tracks) => ({getTracks:()=>tracks});

test('only live tracks are available; toggling affects every local track of the chosen kind', () => {
  const a=track('audio'), b=track('audio'), v=track('video'), ended=track('video',true,'ended');
  const s=stream(a,b,v,ended);
  assert.deepEqual(readLocalMedia(s),{audio:{available:true,enabled:true},video:{available:true,enabled:true}});
  setLocalMediaEnabled(s,'audio',false);
  assert.equal(a.enabled,false); assert.equal(b.enabled,false); assert.equal(v.enabled,true);
  setLocalMediaEnabled(s,'video',false);
  assert.equal(v.enabled,false); assert.equal(ended.enabled,true);
  setLocalMediaEnabled(s,'audio',true);
  assert.equal(a.enabled,true); assert.equal(b.enabled,true);
});
test('unavailable tracks cannot be enabled and invalid kinds and values are rejected', () => {
  assert.deepEqual(readLocalMedia(null),{audio:{available:false,enabled:false},video:{available:false,enabled:false}});
  assert.throws(()=>setLocalMediaEnabled(stream(track('audio',true,'ended')),'audio',true),/unavailable/);
  assert.throws(()=>setLocalMediaEnabled(stream(),'screen',false),/Invalid/);
  assert.throws(()=>setLocalMediaEnabled(stream(),'audio','false'),/Invalid/);
});
test('partial mute failure is reported and remaining tracks are still muted', () => {
  const stuck={kind:'audio',readyState:'live',get enabled(){return true;},set enabled(value){throw new Error('private');}};
  const other=track('audio'); const s=stream(stuck,other);
  assert.throws(()=>setLocalMediaEnabled(s,'audio',false),/Unable/);
  assert.equal(other.enabled,false);
  assert.equal(readLocalMedia(s).audio.enabled,true);
});
test('partial unmute failure attempts to disable all tracks', () => {
  const stuck={kind:'audio',readyState:'live',get enabled(){return false;},set enabled(value){if(value) throw new Error('private');}};
  const other=track('audio',false); const s=stream(stuck,other);
  assert.throws(()=>setLocalMediaEnabled(s,'audio',true),/Unable/);
  assert.equal(other.enabled,false);
  assert.equal(readLocalMedia(s).audio.enabled,false);
});
