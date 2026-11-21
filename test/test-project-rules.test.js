import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {restrictedTestRules} from '../scripts/generate-isolated-rules.mjs';
const source = readFileSync(new URL('../firestore.rules', import.meta.url), 'utf8');

test('restricted test rules allow exactly two validated test identities', () => {
  const result = restrictedTestRules(source, 'test_alice', 'test-bob');
  assert.match(result, /request\.auth\.uid in \['test_alice', 'test-bob'\]/);
  assert.equal(result.includes('function signedIn() { return request.auth != null; }'), false);
  assert.ok(result.includes('match /conversations/{conversationId}'));
});

test('invalid identities and unexpected rule templates fail closed', () => {
  for (const [first, second] of [['same','same'],['alice',"bob' || true"],['alice',''],['alice','x'.repeat(129)], ['alice','bob/other']]) {
    assert.throws(() => restrictedTestRules(source, first, second));
  }
  assert.throws(() => restrictedTestRules('changed template', 'alice', 'bob'));
  assert.throws(() => restrictedTestRules(source + source, 'alice', 'bob'));
});
