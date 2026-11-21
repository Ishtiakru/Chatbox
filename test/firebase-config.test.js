import test from 'node:test';
import assert from 'node:assert/strict';
import {resolveFirebaseConfig} from '../src/firebase-config.js';
const testConfig = {projectId:'chatbox-test-123',apiKey:'public-key',authDomain:'chatbox-test-123.firebaseapp.com',messagingSenderId:'123456',appId:'1:123456:web:abc123'};
const env = config => ({VITE_CHATBOX_TEST_MODE:'true', VITE_CHATBOX_TEST_FIREBASE_CONFIG: JSON.stringify(config)});
test('default stays on existing project, test configuration is opt-in and isolated', () => {
  const prod = resolveFirebaseConfig({});
  assert.equal(prod.config.projectId, 'chatbox-afb8c');
  assert.equal(prod.isTestProject, false);
  const testProject = resolveFirebaseConfig(env({...testConfig,databaseURL:'https://untrusted.invalid'}));
  assert.equal(testProject.isTestProject, true);
  assert.deepEqual(testProject.config,testConfig);
});
test('invalid test config fails closed rather than silently using the real project', () => {
  assert.throws(() => resolveFirebaseConfig({VITE_CHATBOX_TEST_MODE:'true'}));
  assert.throws(() => resolveFirebaseConfig({VITE_CHATBOX_TEST_MODE:'true', VITE_CHATBOX_TEST_FIREBASE_CONFIG:'{'}));
  assert.throws(() => resolveFirebaseConfig({VITE_CHATBOX_TEST_FIREBASE_CONFIG:JSON.stringify(testConfig)}));
  assert.throws(() => resolveFirebaseConfig(env(null)));
  for (const config of [
    {...testConfig,projectId:'chatbox-afb8c',authDomain:'chatbox-afb8c.firebaseapp.com'},
    {...testConfig,projectId:'../not-valid'},
    {...testConfig,authDomain:'chatbox-afb8c.firebaseapp.com'},
    {...testConfig,appId:'1:999:web:abc123'},
    {...testConfig,apiKey:''},
  ]) assert.throws(() => resolveFirebaseConfig(env(config)));
});
