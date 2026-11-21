import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {generateRateRules} from '../scripts/generate-rate-rules.mjs';
const read=path=>readFileSync(path,'utf8');
test('cooldown rules remain an explicitly generated candidate, not a deployment default',()=>{
  const combined=read('firestore.combined-experimental.rules');
  const generated=generateRateRules(combined);
  assert.equal(generated,read('firestore.rate-limited-experimental.rules'));
  assert.match(generated,/getAfter\(path\).*conversationId/);
  assert.match(generated,/duration.value\(2, 's'\)/);
  assert.equal(JSON.parse(read('firebase.json')).firestore,undefined);
  assert.equal(JSON.parse(read('firebase.emulator.json')).firestore.rules,'firestore.rules');
  assert.throws(()=>generateRateRules(generated),/already present/);
  assert.throws(()=>generateRateRules(combined.replace('match /messages/{messageId}','match /messages/{other}').replace('          && request.resource.data.sentAt == request.time;','missing')),/marker/);
});
