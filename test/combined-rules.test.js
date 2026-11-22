import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {combineRules} from '../scripts/combine-spark-call-rules.mjs';
const read = p => readFileSync(p,'utf8');
test('combined rules include both experimental policies without changing the base', () => {
  const base=read('firestore.rules'), spark=read('firestore.spark-admin.rules'), calls=read('firestore.calls.rules');
  const combined=combineRules(base,spark,calls);
  assert.equal(combined,read('firestore.combined-experimental.rules'));
  assert.match(combined,/match \/sparkAdmin\/current/);
  assert.match(combined,/match \/calls\/\{callId\}/);
  assert.throws(()=>combineRules(base,spark,calls.replace("match /profiles/{uid}","match /profiles/{changed}")),/outside its call block/);
  assert.throws(()=>combineRules(base,spark.replace('function signedIn()', 'function modified()'),calls),/before the admin block/);
  assert.throws(()=>combineRules(base,spark,calls.replace('match /messages/{messageId}', 'match /messages/{changed}')),/marker/);
});
