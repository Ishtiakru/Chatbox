import test from 'node:test';
import assert from 'node:assert/strict';
import {validateProductionAndroidPair} from '../scripts/production-android-pair.mjs';
const hash = 'A1'.repeat(20);
const client = {client_info:{mobilesdk_app_id:'1:1030482946047:android:abcdef',android_client_info:{package_name:'com.chatbox.apk'}},api_key:[{current_key:'public-key'}],oauth_client:[{client_type:3,client_id:'web-client'},{client_type:1,client_id:'android-client',android_info:{package_name:'com.chatbox.apk',certificate_hash:hash.toLowerCase()}}]};
const good = {project_info:{project_id:'chatbox-afb8c',project_number:'1030482946047'},client:[client]};

test('production preflight matches the release fingerprint and configuration', () => {
  assert.equal(validateProductionAndroidPair(good,hash.match(/../g).join(':')), true);
  assert.throws(()=>validateProductionAndroidPair(good,'00'.repeat(20)),/release certificate/);
  assert.throws(()=>validateProductionAndroidPair(good,'bogus'),/SHA-1/);
  assert.throws(()=>validateProductionAndroidPair({...good,project_info:{...good.project_info,project_id:'test-project'}},hash),/project ID/);
  assert.throws(()=>validateProductionAndroidPair({...good,project_info:{...good.project_info,project_number:'123'}},hash),/sender number/);
  assert.throws(()=>validateProductionAndroidPair({...good,client:[client,client]},hash),/Exactly one/);
  assert.throws(()=>validateProductionAndroidPair({...good,client:[{...client,client_info:{...client.client_info,mobilesdk_app_id:'1:123:android:abc'}}]},hash),/app ID/);
  assert.throws(()=>validateProductionAndroidPair({...good,client:[{...client,api_key:[]}]},hash),/API key/);
  assert.throws(()=>validateProductionAndroidPair({...good,client:[{...client,oauth_client:[client.oauth_client[1]]}]},hash),/web OAuth/);
  assert.throws(()=>validateProductionAndroidPair({...good,client:[{...client,oauth_client:[client.oauth_client[0],{...client.oauth_client[1],android_info:{...client.oauth_client[1].android_info,package_name:'com.chatbox.test'}}]}]},hash),/release certificate/);
});
