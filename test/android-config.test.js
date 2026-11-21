import test from 'node:test';
import assert from 'node:assert/strict';
import { validateAndroidFirebaseConfig } from '../scripts/android-config.mjs';
const good = {project_info:{project_id:'chatbox-afb8c'},client:[{client_info:{android_client_info:{package_name:'com.chatbox.apk'}},oauth_client:[{client_type:3,client_id:'public-web-client'}]}]};
test('Android setup requires matching project, package, and Google web OAuth client', () => {
  assert.equal(validateAndroidFirebaseConfig(good), true);
  assert.throws(() => validateAndroidFirebaseConfig({...good,project_info:{project_id:'other'}}), /chatbox-afb8c/);
  assert.throws(() => validateAndroidFirebaseConfig({...good,client:[]}), /com.chatbox.apk/);
  assert.throws(() => validateAndroidFirebaseConfig({...good,client:[{...good.client[0],oauth_client:[]}]}), /OAuth/);
});

test('standard Android sync refuses a test-project configuration', async () => {
  const {spawnSync} = await import('node:child_process');
  const result = spawnSync(process.execPath, ['scripts/check-android-firebase.mjs'], {
    encoding:'utf8', env:{...process.env, VITE_CHATBOX_TEST_FIREBASE_CONFIG:'{}'},
  });
  assert.equal(result.status, 1);
  assert.match(result.stderr, /standard Android sync/);
  const marker = spawnSync(process.execPath, ['scripts/check-android-firebase.mjs'], {
    encoding:'utf8', env:{...process.env, VITE_CHATBOX_NATIVE_TEST_MODE:'true'},
  });
  assert.equal(marker.status, 1);
  assert.match(marker.stderr, /standard Android sync/);
});

test('native wrapper identifies the intended app and avoids device backups', async () => {
  const { readFileSync } = await import('node:fs');
  const config = JSON.parse(readFileSync('capacitor.config.json', 'utf8'));
  const manifest = readFileSync('android/app/src/main/AndroidManifest.xml', 'utf8');
  assert.equal(config.appId, 'com.chatbox.apk');
  assert.equal(config.plugins.FirebaseAuthentication.skipNativeAuth, true);
  assert.match(manifest, /android:allowBackup="false"/);
  assert.match(manifest, /android.permission.INTERNET/);
});
