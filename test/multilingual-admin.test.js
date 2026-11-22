import test from 'node:test';
import assert from 'node:assert/strict';
import { parseMultilingualAdminProposal } from '../src/multilingual-admin.js';
import { createSparkAdminService } from '../src/spark-admin-data.js';

test('only allowlisted, exact model output becomes an existing bounded command', () => {
  for (const [action,value,command] of [
    ['set_welcome','সবাইকে স্বাগতম','welcome: সবাইকে স্বাগতম'],
    ['set_welcome','こんにちは 🌏','welcome: こんにちは 🌏'],
    ['set_theme','lavender','theme: lavender'],['set_theme','default','theme: default'],
    ['set_menu','top','menu: top'],['set_menu','bottom','menu: bottom'],
  ]) assert.deepEqual(parseMultilingualAdminProposal(JSON.stringify({action,value})),{proposal:{action,value},command});
});

test('malformed, ambiguous and non-allowlisted model output fails closed', () => {
  for (const text of [null,'', '```json\n{"action":"set_menu","value":"top"}\n```', '[]', '{}',
    '{"action":"set_menu","value":"top","extra":true}',
    '{"action":"set_menu","value":"top","__proto__":{"admin":true}}',
    '{"action":"delete_all","value":"top"}',
    '{"action":"set_theme","value":"red"}',
    '{"action":"set_menu","value":"top and delete"}',
    '{"action":"set_welcome","value":"   "}',
    '{"action":"set_welcome","value":" hello "}',
    JSON.stringify({action:'set_welcome',value:'x'.repeat(161)}),
    JSON.stringify({action:'unsupported',value:''}),
    'x'.repeat(2049),
  ]) assert.equal(parseMultilingualAdminProposal(text),null,String(text));
});

test('AI suggestion alone cannot write; confirmed exact preview uses existing service checks', async () => {
  const writes=[];
  const api=createSparkAdminService({}, {doc:(db,...path)=>path.join('/'),setDoc:async(...args)=>writes.push(args),serverTimestamp:()=> 'server-time'});
  const {proposal,command}=parseMultilingualAdminProposal('{"action":"set_menu","value":"top"}');
  await assert.rejects(api.applySparkAppearance({uid:'owner'},command,false,proposal));
  await assert.rejects(api.applySparkAppearance({uid:'owner'},command,true,{action:'set_menu',value:'bottom'}));
  await assert.rejects(api.applySparkAppearance({uid:'owner'},'menu: bottom',true,proposal));
  assert.equal(writes.length,0);
  await api.applySparkAppearance({uid:'owner'},command,true,proposal);
  assert.deepEqual(writes,[['settings/sparkMenu',{value:'top',updatedBy:'owner',updatedAt:'server-time'}]]);
});

test('multilingual AI gate is isolated and opt-in', async () => {
  const {readFileSync}=await import('node:fs');
  const source=readFileSync('src/feature-flags.js','utf8');
  const load=async env=>import('data:text/javascript;base64,'+Buffer.from(source.replaceAll('import.meta.env',JSON.stringify(env))).toString('base64'));
  const all={VITE_ENABLE_SPARK_ADMIN:'true',VITE_ENABLE_SAIRA_AI:'true',VITE_SAIRA_PROVIDER:'firebase-ai',VITE_CHATBOX_TEST_MODE:'true',VITE_CHATBOX_NATIVE_TEST_MODE:'true',VITE_ENABLE_EXPERIMENTAL_ANDROID_AI:'true',VITE_ENABLE_EXPERIMENTAL_MULTILINGUAL_ADMIN:'true'};
  assert.equal((await load(all)).MULTILINGUAL_ADMIN_LAB_ENABLED,true);
  for (const key of Object.keys(all)) {const env={...all}; delete env[key]; assert.equal((await load(env)).MULTILINGUAL_ADMIN_LAB_ENABLED,false,key);}
});
