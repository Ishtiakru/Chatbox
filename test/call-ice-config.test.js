import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import { experimentalIceServers } from '../src/call-ice-config.js';
import { experimentalCallPanel } from '../src/call-view.js';

test('browser-lab STUN is strictly opt-in and never contains relay credentials', () => {
  for (const flag of [undefined, false, null, 'true', 1]) assert.deepEqual(experimentalIceServers(flag), []);
  const servers=experimentalIceServers(true);
  assert.deepEqual(servers,[{urls:'stun:stun.l.google.com:19302'}]);
  assert.equal('username' in servers[0],false);
  assert.equal('credential' in servers[0],false);
  servers[0].urls='mutated';
  assert.equal(experimentalIceServers(true)[0].urls,'stun:stun.l.google.com:19302');
});

test('STUN requires both experimental chat and calls build flags', () => {
  const flags=readFileSync('src/feature-flags.js','utf8');
  assert.match(flags,/EXPERIMENTAL_CALLS_ENABLED = LIVE_CHAT_ENABLED &&/);
  assert.match(flags,/EXPERIMENTAL_STUN_ENABLED = EXPERIMENTAL_CALLS_ENABLED && import\.meta\.env\.VITE_ENABLE_EXPERIMENTAL_STUN === 'true'/);
  assert.match(readFileSync('src/call-feature.js','utf8'),/experimentalIceServers\(EXPERIMENTAL_STUN_ENABLED\)/);
  assert.match(readFileSync('.env.example','utf8'),/VITE_ENABLE_EXPERIMENTAL_STUN=false/);
});

test('call panel discloses STUN use, peer IP exposure and missing TURN relay', () => {
  const call={status:'idle',incoming:[],error:''};
  const off=experimentalCallPanel(call);
  assert.match(off,/No STUN server is configured/);
  assert.doesNotMatch(off,/Google STUN may receive/);
  const on=experimentalCallPanel(call,{stunEnabled:true});
  assert.match(on,/Google STUN may receive your IP address/);
  assert.match(on,/no TURN relay/);
  assert.match(on,/IP address may be visible to the other participant/);
});
