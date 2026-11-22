import test from 'node:test';
import assert from 'node:assert/strict';
import {matchesLiveConversation, visiblePeerName} from '../src/live-search.js';

const conversation = {id:'chat-1', participants:['owner', 'peer-123']};
const profile = {uid:'peer-123', status:'ready', name:'Maya Rahman'};
const profiles = new Map([['peer-123', profile]]);
const matches = (text, selected = '', selectedProfile = null, map = profiles) =>
  matchesLiveConversation(conversation, 'owner', text, selected, selectedProfile, map);

test('search filters existing conversations by name or exact peer ID substring', () => {
  assert.equal(matches('  MAYA  '), true);
  assert.equal(matches('rah'), true);
  assert.equal(matches('PEER-12'), true);
  assert.equal(matches('missing'), false);
  assert.equal(matches(''), true);
  assert.equal(matches('   '), true);
  assert.equal(matches('owner'), false);
});

test('only currently confirmed matching profile names participate in search', () => {
  assert.equal(matches('maya', '', null, new Map([['peer-123', {...profile, status:'unavailable'}]])), false);
  assert.equal(matches('maya', '', null, new Map([['peer-123', {...profile, uid:'someone-else'}]])), false);
  assert.equal(matches('maya', 'chat-1', {uid:'other', status:'ready', name:'Other'}), true);
  assert.equal(matches('new name', 'chat-1', {...profile, name:'New name'}), true);
  assert.equal(matches('maya', 'chat-1', {...profile, name:'New name'}), false);
  assert.equal(visiblePeerName('peer-123', {uid:'someone-else', status:'ready', name:'Impostor'}), 'peer-123');
});

test('malformed peer data does not match a search', () => {
  assert.equal(matchesLiveConversation({participants:null}, 'owner', 'maya', '', null, profiles), false);
  assert.equal(matchesLiveConversation({participants:['owner', 4]}, 'owner', 'maya', '', null, profiles), false);
  assert.equal(matchesLiveConversation(null, 'owner', 'maya', '', null, profiles), false);
});
