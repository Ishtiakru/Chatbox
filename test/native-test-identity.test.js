import test from 'node:test';
import assert from 'node:assert/strict';
import {verifyNativeTestIdentity} from '../src/native-test-identity.js';

const web = {projectId:'chatbox-test-123',apiKey:'public-key',authDomain:'chatbox-test-123.firebaseapp.com',messagingSenderId:'123456',appId:'1:123456:web:abc123'};
const native = {projectId:'chatbox-test-123',senderId:'123456',androidAppId:'1:123456:android:abc123',packageName:'com.chatbox.test',debuggable:true};

test('native bridge is registered and exposes identity only', async () => {
  const {readFileSync} = await import('node:fs');
  const main = readFileSync('android/app/src/main/java/com/chatbox/apk/MainActivity.java', 'utf8');
  const bridge = readFileSync('android/app/src/main/java/com/chatbox/apk/NativeFirebaseIdentityPlugin.java', 'utf8');
  assert.match(main, /registerPlugin\(NativeFirebaseIdentityPlugin\.class\)/);
  assert.match(bridge, /FLAG_DEBUGGABLE/);
  assert.match(bridge, /google_app_id/);
  assert.doesNotMatch(bridge, /getIdToken|signInWith|api_key/);
});

test('isolated native identity must match the Firebase test project and debug package', () => {
  assert.equal(verifyNativeTestIdentity(web, native), true);
  for (const identity of [
    null,
    {...native,debuggable:false},
    {...native,debuggable:'true'},
    {...native,packageName:'com.chatbox.apk'},
    {...native,projectId:'chatbox-afb8c'},
    {...native,senderId:'999999'},
    {...native,androidAppId:'1:999999:android:abc123'},
    {...native,androidAppId:'1:123456:web:abc123'},
  ]) assert.throws(() => verifyNativeTestIdentity(web, identity));
  assert.throws(() => verifyNativeTestIdentity({...web, projectId:'chatbox-afb8c',authDomain:'chatbox-afb8c.firebaseapp.com'}, native));
});
