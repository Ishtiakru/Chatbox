import test from 'node:test';
import assert from 'node:assert/strict';
import {createForegroundMediaDevices} from '../src/foreground-media.js';
function deferred(){let resolve;const promise=new Promise(r=>{resolve=r;});return {promise,resolve};}
test('foreground media passes through the exact constraints and stream',async()=>{
  const stream={getTracks:()=>[]}, constraints={audio:true,video:true};let got;
  const wrapped=createForegroundMediaDevices({getUserMedia:async value=>{got=value;return stream;}},()=>true);
  assert.equal(await wrapped.getUserMedia(constraints),stream);assert.equal(got,constraints);
});
test('a hidden page never opens a permission prompt',async()=>{
  let requested=0;const wrapped=createForegroundMediaDevices({getUserMedia:async()=>{requested++;}},()=>false);
  await assert.rejects(wrapped.getUserMedia({audio:true}),/not available/);assert.equal(requested,0);
});
test('a late permission grant after losing availability stops every track',async()=>{
  let available=true;const request=deferred(),stopped=[];
  const wrapped=createForegroundMediaDevices({getUserMedia:()=>request.promise},()=>available);
  const pending=wrapped.getUserMedia({video:true});available=false;
  request.resolve({getTracks:()=>[{stop(){stopped.push('audio');throw new Error('track failure');}},{stop(){stopped.push('video');}}]});
  await assert.rejects(pending,/not available/);assert.deepEqual(stopped,['audio','video']);
});
test('an exception checking availability fails closed and releases late media',async()=>{
  let first=true;const stopped=[];
  const wrapped=createForegroundMediaDevices({getUserMedia:async()=>({getTracks:()=>[{stop(){stopped.push('stopped');}}]})},()=>{if(first){first=false;return true;}throw new Error('unknown page');});
  await assert.rejects(wrapped.getUserMedia({audio:true}),/not available/);assert.deepEqual(stopped,['stopped']);
});
