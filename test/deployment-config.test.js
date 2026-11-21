import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
const read=path=>JSON.parse(readFileSync(path,'utf8'));

test('default Firebase configuration selects only Hosting, not experimental rules or billed Functions', () => {
  const config=read('firebase.json');
  assert.deepEqual(Object.keys(config),['hosting']);
  assert.equal(config.hosting.public,'dist');
});

test('rules scripts use explicit local config and demo project, not the live default project', () => {
  const config=read('firebase.emulator.json');
  assert.equal(config.firestore.rules,'firestore.rules');
  assert.equal(config.emulators.firestore.host,'127.0.0.1');
  assert.equal(config.functions,undefined);
  const scripts=read('package.json').scripts;
  for (const name of ['test:rules','test:rules:alternate','test:combined','test:combined:alternate','test:rate','test:rate:alternate','test:request-rate','test:request-rate:alternate']) {
    assert.match(scripts[name],/emulators:exec/);
    assert.match(scripts[name],/--config firebase\.emulator\.json/);
    assert.match(scripts[name],/--project demo-chatbox/);
    assert.doesNotMatch(scripts[name],/\bdeploy\b/);
  }
});
