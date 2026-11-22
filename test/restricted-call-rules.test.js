import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {restrictedCallTestRules} from '../scripts/generate-isolated-call-rules.mjs';

const source=readFileSync('firestore.calls.rules','utf8');
test('restricted call rules select exactly two identities and retain call signaling',()=>{
  const rules=restrictedCallTestRules(source,'alice','bob');
  assert.match(rules,/request\.auth\.uid in \['alice', 'bob'\]/);
  assert.match(rules,/match \/calls\/\{callId\}/);
  assert.doesNotMatch(rules,/function signedIn\(\) \{ return request\.auth != null; \}/);
});
test('restricted call generation fails closed on bad IDs or a changed candidate',()=>{
  for (const pair of [['same','same'],['alice',''],['alice','x'.repeat(129)],['alice','bob/other'],['alice',"b' || true"]]) {
    assert.throws(()=>restrictedCallTestRules(source,...pair));
  }
  assert.throws(()=>restrictedCallTestRules(source+'\n','alice','bob'));
  assert.throws(()=>restrictedCallTestRules(null,'alice','bob'));
});
