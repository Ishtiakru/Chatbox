import test from 'node:test';
import assert from 'node:assert/strict';
import {validateIsolatedAndroidPair} from '../scripts/isolated-android-config.mjs';

const fingerprint = '0123456789ABCDEF0123456789ABCDEF01234567';
const web = {projectId:'chatbox-test-123',apiKey:'public-key',authDomain:'chatbox-test-123.firebaseapp.com',messagingSenderId:'123456',appId:'1:123456:web:abcdef'};
const android = {
  project_info:{project_id:'chatbox-test-123',project_number:'123456'},
  client:[{
    client_info:{mobilesdk_app_id:'1:123456:android:abc123',android_client_info:{package_name:'com.chatbox.test'}},
    api_key:[{current_key:'public-android-key'}],
    oauth_client:[
      {client_type:3,client_id:'public-web-client'},
      {client_type:1,client_id:'public-android-client',android_info:{package_name:'com.chatbox.test',certificate_hash:fingerprint.toLowerCase()}},
    ],
  }],
};
const copy = value => structuredClone(value);

test('an isolated native configuration must match its web project, package and certificate', () => {
  assert.equal(validateIsolatedAndroidPair(android, web, fingerprint.match(/../g).join(':')), true);
  const project = copy(android); project.project_info.project_id = 'chatbox-afb8c';
  const number = copy(android); number.project_info.project_number = '999999';
  const wrongPackage = copy(android); wrongPackage.client[0].client_info.android_client_info.package_name = 'com.chatbox.apk';
  const duplicate = copy(android); duplicate.client.push(copy(duplicate.client[0]));
  const wrongApp = copy(android); wrongApp.client[0].client_info.mobilesdk_app_id = '1:999999:android:abc123';
  const noKey = copy(android); noKey.client[0].api_key = [];
  const noWebOauth = copy(android); noWebOauth.client[0].oauth_client = noWebOauth.client[0].oauth_client.filter(c => c.client_type !== 3);
  const wrongCert = copy(android); wrongCert.client[0].oauth_client[1].android_info.certificate_hash = 'F'.repeat(40);
  const wrongOAuthPackage = copy(android); wrongOAuthPackage.client[0].oauth_client[1].android_info.package_name = 'com.chatbox.apk';
  for (const value of [project,number,wrongPackage,duplicate,wrongApp,noKey,noWebOauth,wrongCert,wrongOAuthPackage]) {
    assert.throws(() => validateIsolatedAndroidPair(value, web, fingerprint));
  }
});

test('read-only CLI reports configuration errors without printing file contents', async () => {
  const {mkdtempSync,writeFileSync,rmSync} = await import('node:fs');
  const {tmpdir} = await import('node:os');
  const {join} = await import('node:path');
  const {spawnSync} = await import('node:child_process');
  const dir = mkdtempSync(join(tmpdir(), 'chatbox-android-pair-'));
  try {
    const androidPath = join(dir, 'android.json');
    const webPath = join(dir, 'web.json');
    writeFileSync(androidPath, JSON.stringify(android));
    writeFileSync(webPath, JSON.stringify(web));
    const run = sha => spawnSync(process.execPath, ['scripts/check-isolated-android-pair.mjs', androidPath, webPath, sha], {encoding:'utf8'});
    const valid = run(fingerprint);
    assert.equal(valid.status, 0);
    assert.match(valid.stdout, /No APK was built/);
    assert.doesNotMatch(valid.stdout, /public-android-key|public-web-client/);
    const invalid = run('F'.repeat(40));
    assert.equal(invalid.status, 1);
    assert.match(invalid.stderr, /does not match/);
    assert.doesNotMatch(invalid.stderr, /public-android-key|public-web-client/);
  } finally { rmSync(dir, {recursive:true,force:true}); }
});

test('refuses missing or malformed fingerprints, incomplete web config and the production project', () => {
  for (const fingerprintValue of [undefined, '', 'x'.repeat(40), 'A'.repeat(39)]) {
    assert.throws(() => validateIsolatedAndroidPair(android, web, fingerprintValue));
  }
  assert.throws(() => validateIsolatedAndroidPair(android, {...web, appId:'bad'}, fingerprint));
  const production = {...web,projectId:'chatbox-afb8c',authDomain:'chatbox-afb8c.firebaseapp.com'};
  assert.throws(() => validateIsolatedAndroidPair(android, production, fingerprint));
});
