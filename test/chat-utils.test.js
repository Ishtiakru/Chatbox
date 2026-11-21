import test from 'node:test';
import assert from 'node:assert/strict';
import { ChatInputError, validPeerUid, normalizeMessage, otherParticipant, chatErrorMessage } from '../src/chat-utils.js';

test('direct chats require a different, path-safe user ID', () => {
  assert.equal(validPeerUid('bob', 'alice'), true);
  for (const uid of ['', 'alice', 'a/b', 'x'.repeat(129)]) assert.equal(validPeerUid(uid, 'alice'), false);
});
test('message text is trimmed and bounded', () => {
  assert.equal(normalizeMessage('  hello  '), 'hello');
  assert.throws(() => normalizeMessage('   '));
  assert.throws(() => normalizeMessage('x'.repeat(2001)));
});
test('peer and error display helpers', () => {
  assert.equal(otherParticipant(['a', 'b'], 'a'), 'b');
  assert.equal(otherParticipant(null, 'a'), '');
  assert.match(chatErrorMessage({code:'permission-denied'}), /rules/);
});
test('chat errors expose only allowlisted local validation messages', () => {
  assert.match(chatErrorMessage(new ChatInputError('invalid-peer')), /valid Chatbox ID/);
  assert.match(chatErrorMessage(new ChatInputError('invalid-message')), /2000/);
  assert.match(chatErrorMessage(new ChatInputError('profile-not-found')), /No Chatbox profile/);
  assert.equal(chatErrorMessage(new ChatInputError('constructor')), 'Something went wrong. Please try again.');
  assert.equal(chatErrorMessage(new Error('internal-project-token')), 'Something went wrong. Please try again.');
  assert.equal(chatErrorMessage({code:'invalid-peer', message:'internal detail'}), 'Something went wrong. Please try again.');
});
