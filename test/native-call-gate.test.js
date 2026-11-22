import test from 'node:test';
import assert from 'node:assert/strict';
import { verifyNativeCallLab } from '../src/native-call-gate.js';
const web={projectId:'chatbox-test-123',apiKey:'test',authDomain:'chatbox-test-123.firebaseapp.com',messagingSenderId:'123456',appId:'1:123456:web:abc'};
const identity={packageName:'com.chatbox.test',debuggable:true,projectId:'chatbox-test-123',senderId:'123456',androidAppId:'1:123456:android:abc',callLabEnabled:true};
const options={isTestProject:true,enabled:true,nativeTestMode:true,webConfig:web,getIdentity:async()=>identity};
test('Android call lab requires test project, explicit flag, debug package and matching native identity', async () => {
  assert.equal(await verifyNativeCallLab(options),true);
  for (const overrides of [{isTestProject:false},{enabled:false},{nativeTestMode:false},{getIdentity:null},
    ...[{packageName:'com.chatbox.apk'},{debuggable:false},{projectId:'chatbox-afb8c'},{senderId:'1'},{androidAppId:'wrong'},{callLabEnabled:false}].map(x=>({getIdentity:async()=>({...identity,...x})}))]) {
    await assert.rejects(verifyNativeCallLab({...options,...overrides}));
  }
});
