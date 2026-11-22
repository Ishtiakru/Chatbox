import test from 'node:test';
import assert from 'node:assert/strict';
import {canUseCallPage, createNativeCallLifecycle} from '../src/call-page-lifecycle.js';

const foreground = {enabled:true,pageActive:true,hidden:false,view:'live',authenticated:true};
test('calling is available only to the current signed-in user on the visible live page',()=>{
  assert.equal(canUseCallPage(foreground),true);
  for(const patch of [
    {enabled:false},{pageActive:false},{hidden:true},{view:'chats'},
    {view:'admin'},{authenticated:false}, {hidden:undefined}, {pageActive:undefined}, {nativeActive:false},
  ]) assert.equal(canUseCallPage({...foreground,...patch}),false,JSON.stringify(patch));
});

test('native stop blocks new calls before teardown and start permits discovery again', () => {
  const seen=[];
  let lifecycle;
  lifecycle=createNativeCallLifecycle({
    onStop:()=>seen.push(['stop',canUseCallPage({...foreground,nativeActive:lifecycle.isActive()})]),
    onStart:()=>seen.push(['start',canUseCallPage({...foreground,nativeActive:lifecycle.isActive()})]),
  });
  assert.equal(lifecycle.isActive(),true);
  lifecycle.stop();
  assert.equal(lifecycle.isActive(),false);
  lifecycle.start();
  assert.deepEqual(seen,[['stop',false],['start',true]]);
  assert.throws(()=>createNativeCallLifecycle({onStop:()=>{}}),/required/);
});
