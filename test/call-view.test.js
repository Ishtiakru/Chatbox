import test from 'node:test';
import assert from 'node:assert/strict';
import { experimentalCallPanel } from '../src/call-view.js';
test('call lab requires a separate explicit receiver acceptance and escapes call IDs', () => {
  const list = experimentalCallPanel({status:'idle',incoming:[{id:'evil"<x>',media:'audio'}],error:''});
  assert.match(list, /Review incoming audio call/);
  assert.ok(!list.includes('evil"<x>'));
  assert.ok(!list.includes('data-action="call-accept"'));
  const offer = experimentalCallPanel({status:'incoming',incoming:[],error:''});
  assert.match(offer, /data-action="call-accept"/);
  assert.match(offer, /data-action="call-hangup"/);
  assert.ok(!offer.includes('data-action="call-audio"'));
});

test('media controls appear only after capture, reflect state and explain their limits', () => {
  const base={status:'incoming',incoming:[],error:'',media:'video',localStream:null};
  assert.doesNotMatch(experimentalCallPanel(base),/call-mic-toggle|call-camera-toggle/);
  const live={...base,status:'connected',localStream:{},localMedia:{audio:{available:true,enabled:true},video:{available:true,enabled:false}}};
  const html=experimentalCallPanel(live);
  assert.match(html,/Mute microphone/); assert.match(html,/Turn camera on/);
  assert.match(html,/does not revoke device permission/);
  const muted=experimentalCallPanel({...live,localMedia:{audio:{available:true,enabled:false},video:{available:false,enabled:false}}});
  assert.match(muted,/Unmute microphone/); assert.match(muted,/Camera unavailable/);
  const audioOnly=experimentalCallPanel({...live,media:'audio'});
  assert.doesNotMatch(audioOnly,/call-camera-toggle/);
});
