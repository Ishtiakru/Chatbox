import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {restrictedCombinedTestRules} from '../scripts/generate-isolated-combined-rules.mjs';
const source = readFileSync(new URL('../firestore.combined-experimental.rules',import.meta.url),'utf8');
test('restricted combined rules limit signed-in access to two validated UIDs',()=>{
  const result = restrictedCombinedTestRules(source,'alice','bob');
  assert.match(result, /request\.auth\.uid in \['alice', 'bob'\]/);
  assert.match(result, /match \/sparkAdmin\/current/);
  assert.match(result, /match \/calls\/\{callId\}/);
  assert.match(result, /match \/messages\/\{messageId\}/);
});
test('restricted combined generator fails closed on changed source or invalid UIDs',()=>{
  for (const args of [['alice','alice'],['','bob'],["alice' || true",'bob'],['alice','a/b']]) assert.throws(()=>restrictedCombinedTestRules(source,...args));
  assert.throws(()=>restrictedCombinedTestRules(source+' ', 'alice','bob'), /changed/);
});
