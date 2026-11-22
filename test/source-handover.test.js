import test from 'node:test';
import assert from 'node:assert/strict';
import { validateSourceHandover } from '../src/source-handover.js';
import { createSparkAdminService } from '../src/spark-admin-data.js';

test('source handover accepts only canonical HTTPS Drive file paths', () => {
  const commit = 'a'.repeat(40);
  assert.deepEqual(validateSourceHandover({url:'https://drive.google.com/file/d/abcdefghij_12/view?usp=sharing',commit}), {url:'https://drive.google.com/file/d/abcdefghij_12/view',commit});
  assert.equal(validateSourceHandover({url:'https://drive.google.com/file/d/abcdefghij_12/view',commit:'old'}).commit,'');
  for (const url of [null,'javascript:alert(1)','http://drive.google.com/file/d/abcdefghij/view','https://drive.google.com.evil.example/file/d/abcdefghij/view','https://drive.google.com:444/file/d/abcdefghij/view','https://u:p@drive.google.com/file/d/abcdefghij/view','https://drive.google.com/file/d/abcdefghij/view#fragment','https://drive.google.com/file/d/../view','https://drive.google.com/open?id=abcdefghij']) {
    assert.throws(() => validateSourceHandover({url}), /Invalid source handover/);
  }
});

test('source handover reads only the fixed record from the server', async () => {
  const seen=[];
  const data = {url:'https://drive.google.com/file/d/abcdefghij/view'};
  const api = createSparkAdminService({}, {
    doc:(db,...path) => path.join('/'),
    getDocFromServer:async path => {seen.push(path); return {exists:()=>true,data:()=>data};},
  });
  assert.deepEqual(await api.getSparkSourceHandover(),data);
  assert.deepEqual(seen,['sparkPrivate/sourceHandover']);
  const missing = createSparkAdminService({}, {doc:()=>'',getDocFromServer:async()=>({exists:()=>false})});
  await assert.rejects(missing.getSparkSourceHandover(), /not configured/);
  const denied = createSparkAdminService({}, {doc:()=>'',getDocFromServer:async()=>{throw new Error('denied');}});
  await assert.rejects(denied.getSparkSourceHandover(), /denied/);
});

test('source rules are a separate generated review candidate', async () => {
  const {readFileSync} = await import('node:fs');
  const {sparkSourceRules} = await import('../scripts/generate-spark-source-rules.mjs');
  const spark = readFileSync('firestore.spark-admin.rules','utf8');
  const source = readFileSync('firestore.spark-source-experimental.rules','utf8');
  assert.equal(sparkSourceRules(spark),source);
  assert.equal(sparkSourceRules(readFileSync('firestore.combined-experimental.rules','utf8')),readFileSync('firestore.combined-source-experimental.rules','utf8'));
  assert.doesNotMatch(spark,/match \/sparkPrivate\/sourceHandover/);
  assert.throws(() => sparkSourceRules(source));
  assert.throws(() => sparkSourceRules('unexpected'));
});

test('Spark source handover is disabled unless both admin and isolated test gates are enabled', async () => {
  const {readFileSync} = await import('node:fs');
  const source=readFileSync('src/feature-flags.js','utf8');
  const load=async env => import('data:text/javascript;base64,'+Buffer.from(source.replaceAll('import.meta.env',JSON.stringify(env))).toString('base64'));
  for (const env of [{}, {VITE_ENABLE_SPARK_SOURCE_HANDOVER:'true'}, {VITE_ENABLE_SPARK_ADMIN:'true',VITE_ENABLE_SPARK_SOURCE_HANDOVER:'true'}, {VITE_CHATBOX_TEST_MODE:'true',VITE_ENABLE_SPARK_SOURCE_HANDOVER:'true'}]) {
    assert.equal((await load(env)).SPARK_SOURCE_HANDOVER_ENABLED,false);
  }
  assert.equal((await load({VITE_CHATBOX_TEST_MODE:'true',VITE_ENABLE_SPARK_ADMIN:'true',VITE_ENABLE_SPARK_SOURCE_HANDOVER:'true'})).SPARK_SOURCE_HANDOVER_ENABLED,true);
});
