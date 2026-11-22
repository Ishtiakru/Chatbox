import test from 'node:test';
import assert from 'node:assert/strict';
import {blockControls} from '../src/block-view.js';

test('experimental blocking controls distinguish checking, saving, and confirmed states', () => {
  assert.match(blockControls({status:'loading'},'bob',false),/Checking/);
  assert.match(blockControls({status:'saving'},'bob',false),/Do not rely on it yet/);
  assert.match(blockControls({status:'ready',ids:[]},'bob',false),/Block account and close chat/);
  assert.match(blockControls({status:'ready',ids:['bob']},'bob',false),/Unblock account/);
  assert.match(blockControls({status:'error',error:'private'},'bob',false),/Reconnect blocking/);
  assert.doesNotMatch(blockControls({status:'error',error:'private'},'bob',false),/private/);
  assert.match(blockControls({status:'ready',ids:[]},'bob',true),/disabled/);
  assert.equal(blockControls(null,'bob',false),'');
});
