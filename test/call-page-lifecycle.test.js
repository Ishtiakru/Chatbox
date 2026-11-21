import test from 'node:test';
import assert from 'node:assert/strict';
import {canUseCallPage} from '../src/call-page-lifecycle.js';

const foreground = {enabled:true,pageActive:true,hidden:false,view:'live',authenticated:true};
test('calling is available only to the current signed-in user on the visible live page',()=>{
  assert.equal(canUseCallPage(foreground),true);
  for(const patch of [
    {enabled:false},{pageActive:false},{hidden:true},{view:'chats'},
    {view:'admin'},{authenticated:false}, {hidden:undefined}, {pageActive:undefined},
  ]) assert.equal(canUseCallPage({...foreground,...patch}),false,JSON.stringify(patch));
});
