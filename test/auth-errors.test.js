import test from 'node:test';
import assert from 'node:assert/strict';
import { authErrorMessage } from '../src/auth-errors.js';

test('actionable auth errors do not expose raw server messages', () => {
  assert.match(authErrorMessage('auth/unauthorized-domain'), /Authorized domains/);
  assert.match(authErrorMessage('auth/operation-not-allowed'), /Google/);
  assert.match(authErrorMessage('auth/popup-blocked'), /pop-up/);
  assert.doesNotMatch(authErrorMessage('unknown secret'), /unknown secret/);
});
