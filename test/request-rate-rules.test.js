import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {generateRequestRateRules} from '../scripts/generate-request-rate-rules.mjs';
const read=path=>readFileSync(path,'utf8');
test('request cooldown is an isolated generated candidate, not a deployment default',()=>{
  const base=read('firestore.rate-limited-experimental.rules');
  const rules=generateRequestRateRules(base);
  assert.equal(rules,read('firestore.request-limited-experimental.rules'));
  assert.match(rules,/duration.value\(30, 's'\)/);
  assert.match(rules,/hasRequestReservation\(conversationId\)/);
  assert.match(rules,/duration.value\(2, 's'\)/);
  assert.equal(JSON.parse(read('firebase.json')).firestore,undefined);
  assert.throws(()=>generateRequestRateRules(rules),/already present/);
  assert.throws(()=>generateRequestRateRules(read('firestore.rules')),/Message cooldown candidate required/);
  assert.throws(()=>generateRequestRateRules(base.replace('        && exists(/databases/$(database)/documents/profiles/$(request.resource.data.participants[1]));','missing')),/marker/);
});
