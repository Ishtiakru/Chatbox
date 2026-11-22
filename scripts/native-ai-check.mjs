// Isolated JavaScript bridge/UI checks. All native and Firebase dependencies are
// mocks; these do not verify Android runtime, Play Integrity, or a real model.
import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { SourceTextModule, SyntheticModule, createContext } from 'node:vm';

async function load(file, dependencies) {
  const context = createContext({console});
  const source = await readFile(new URL(file, import.meta.url), 'utf8');
  const module = new SourceTextModule(source, {context});
  await module.link(async specifier => {
    const exports = dependencies[specifier];
    if (!exports) throw new Error(`Unexpected import: ${specifier}`);
    return new SyntheticModule(Object.keys(exports), function () {
      for (const [key, value] of Object.entries(exports)) this.setExport(key, value);
    }, {context});
  });
  await module.evaluate();
  return module.namespace;
}
function mocks({enabled=true, platform='android', ask, propose} = {}) {
  const calls=[];
  const plugin={ask:ask || (async payload => { calls.push(payload); return {answer:'hello'}; }), proposeAdminChange:propose || (async payload => { calls.push(payload); return {answer:'{\"action\":\"set_menu\",\"value\":\"top\"}'}; })};
  const dependencies={
    '@capacitor/core': {Capacitor:{isNativePlatform:()=>platform !== 'web',getPlatform:()=>platform},registerPlugin:()=>plugin},
    './feature-flags.js': {ANDROID_AI_LAB_ENABLED:enabled,MULTILINGUAL_ADMIN_LAB_ENABLED:enabled},
    'firebase/ai': {getAI:()=>{},getGenerativeModel:()=>{},GoogleAIBackend:class {}},
    './firebase.js': {app:{}}, './app-check.js': {ensureAppCheck:()=>{}},
    './saira-ai-adapter.js': {askSairaWithModel:()=>{}},
    '../docs/saira-knowledge.md?raw': {default:'reviewed knowledge'},
  };
  return {dependencies,calls};
}

test('native AI forwards only bounded question and curated knowledge', async () => {
  const {dependencies,calls}=mocks();
  const service=await load('../src/firebase-ai-service.js',dependencies);
  const response=await service.askFirebaseSaira(' hello ');
  assert.equal(response.answer,'hello');
  assert.equal(calls.length,1);
  assert.equal(calls[0].question,'hello');
  assert.equal(calls[0].knowledge,'reviewed knowledge');
  assert.deepEqual(Object.keys(calls[0]).sort(),['knowledge','question']);
});

test('native AI denies disabled labs, non-Android and invalid input', async () => {
  for (const options of [{enabled:false},{platform:'ios'}]) {
    const {dependencies,calls}=mocks(options);
    const service=await load('../src/firebase-ai-service.js',dependencies);
    await assert.rejects(service.askFirebaseSaira('hello'), /not configured/);
    assert.equal(calls.length,0);
  }
  const {dependencies,calls}=mocks();
  const service=await load('../src/firebase-ai-service.js',dependencies);
  for (const question of ['', 'x'.repeat(1001), null]) await assert.rejects(service.askFirebaseSaira(question), /Invalid/);
  assert.equal(calls.length,0);
});

test('native AI rejects empty responses, limits length and propagates failure', async () => {
  const empty=await load('../src/firebase-ai-service.js',mocks({ask:async()=>({answer:''})}).dependencies);
  await assert.rejects(empty.askFirebaseSaira('hi'), /unavailable/);
  const long=await load('../src/firebase-ai-service.js',mocks({ask:async()=>({answer:'x'.repeat(3999)+'🙂'})}).dependencies);
  assert.equal((await long.askFirebaseSaira('hi')).answer,'x'.repeat(3999));
  const blocked=await load('../src/firebase-ai-service.js',mocks({ask:async()=>{throw new Error('App Check is unavailable.');}}).dependencies);
  await assert.rejects(blocked.askFirebaseSaira('hi'), /App Check is unavailable/);
});

async function views({enabled=true,native=true,adminAI=false}={}) {
  return load('../src/cloud-views.js',{
    './knowledge.js': {escapeHtml:s=>String(s).replaceAll('&','&amp;').replaceAll('<','&lt;').replaceAll('>','&gt;').replaceAll('"','&quot;').replaceAll("'",'&#39;')},
    '@capacitor/core': {Capacitor:{isNativePlatform:()=>native,getPlatform:()=>native ? 'android' : 'web'}},
    './feature-flags.js': {SAIRA_AI_ENABLED:enabled,SAIRA_PROVIDER:'firebase-ai',ANDROID_AI_LAB_ENABLED:enabled,MULTILINGUAL_ADMIN_LAB_ENABLED:adminAI,ADMIN_BACKEND_ENABLED:false,APP_CHECK_SITE_KEY:''},
  });
}

test('AI view requires sign-in and escapes displayed question and answer', async () => {
  const view=await views();
  assert.match(view.sairaAiView({user:null,authLoading:false}),/Sign in to talk to Saira/);
  const html=view.sairaAiView({user:{uid:'mock'},ai:{busy:false,error:'',draft:'',messages:[{question:'<script>',answer:'<img>'}]}});
  assert.match(html,/&lt;script&gt;/); assert.match(html,/&lt;img&gt;/);
  assert.doesNotMatch(html,/<script>|<img>/);
  assert.match(html,/Your question is sent to Google Gemini/);
  const disabled=await views({enabled:false});
  assert.match(disabled.sairaAiView({}),/being prepared/);
});

test('admin dashboard denies unsigned users and escapes dynamic content', async () => {
  const view=await views();
  assert.match(view.sparkAdminControls({user:null,authLoading:false,admin:{}}),/Sign in with Google/);
  const html=view.sparkAdminControls({user:{uid:'mock'},authLoading:false,admin:{status:'authorized',role:'owner',busy:false,command:'<script>',target:'',delegations:[{email:'<img>',status:'active'}]}});
  assert.match(html,/&lt;script&gt;/); assert.match(html,/&lt;img&gt;/);
  assert.doesNotMatch(html,/<script>|<img>/);
});

test('private Spark dashboard keeps an empty request field and confirmation controls', async () => {
  const view = await views();
  const admin = {status:'authorized',role:'owner',busy:false,command:'',target:'',delegations:[]};
  const state = {user:{uid:'mock'},authLoading:false,admin};
  const empty = view.sparkAdminControls(state);
  assert.match(empty, /<textarea id="admin-command"[^>]*><\/textarea>/);
  assert.doesNotMatch(empty, /<textarea[^>]*placeholder=/);
  assert.match(empty, /Limited changes only/);
  assert.match(empty, /data-admin-security/);
  assert.match(empty, /data-action="spark-reauth"/);
  assert.match(empty, /id="admin-confirm"/);
  assert.match(empty, /id="admin-transfer-form"/);
  const proposed = view.sparkAdminControls({...state,admin:{...admin,securityOpen:true,proposal:{action:'set_welcome',value:'<unsafe>'}}});
  assert.match(proposed, /data-admin-security open/);
  assert.match(proposed, /PREVIEW — NOT YET APPLIED/);
  assert.match(proposed, /&lt;unsafe&gt;/);
  assert.match(proposed, /data-action="admin-apply"/);
  const denied = view.sparkAdminControls({...state,admin:{...admin,status:'forbidden'}});
  assert.doesNotMatch(denied, /id="admin-command"/);
});

test('dashboard shows honest analytics and retains security section state', async () => {
  const view = await views();
  const state = {user:{uid:'mock'},authLoading:false,admin:{status:'authorized',role:'owner',command:'',target:'',delegations:[],analyticsOpen:true,securityOpen:true}};
  const html = view.sparkAdminControls(state);
  assert.match(html, /class="dashboard-prompt"/);
  assert.match(html, /data-admin-analytics open/);
  assert.match(html, /Analytics is not connected in this build/);
  assert.match(html, /data-admin-security open/);
  assert.match(html, /data-action="admin-apply"|id="admin-command-form"/);
  assert.doesNotMatch(view.sparkAdminControls({...state,admin:{...state.admin,status:'forbidden'}}),/data-admin-analytics|id="admin-command-form"/);
});


test('multilingual admin bridge sends only the request on enabled Android', async () => {
  const {dependencies,calls}=mocks();
  const service=await load('../src/firebase-ai-service.js',dependencies);
  assert.equal(await service.proposeNativeAdminChange('  উপরে মেনু  '), '{"action":"set_menu","value":"top"}');
  assert.equal(calls.length,1); assert.deepEqual(Object.keys(calls[0]),['request']);
  assert.equal(calls[0].request,'উপরে মেনু');
  for (const value of ['',null,'x'.repeat(1001)]) await assert.rejects(service.proposeNativeAdminChange(value), /Invalid/);
  assert.equal(calls.length,1);
  for (const options of [{enabled:false},{platform:'ios'},{platform:'web'}]) {
    const disabled=mocks(options); const other=await load('../src/firebase-ai-service.js',disabled.dependencies);
    await assert.rejects(other.proposeNativeAdminChange('test'), /not configured/);
    assert.equal(disabled.calls.length,0);
  }
  const oversized=await load('../src/firebase-ai-service.js',mocks({propose:async()=>({answer:'x'.repeat(2049)})}).dependencies);
  await assert.rejects(oversized.proposeNativeAdminChange('test'),/unavailable/);
});


test('experimental multilingual admin discloses the provider and requests review', async () => {
  const view = await views({adminAI:true});
  const html = view.sparkAdminControls({user:{uid:'mock'},admin:{status:'authorized',role:'delegate',command:'',target:'',delegations:[]}});
  assert.match(html,/Google Gemini/); assert.match(html,/Do not include sensitive information/);
  assert.match(html,/confirmation required/);
  const web = await views({adminAI:true,native:false});
  assert.doesNotMatch(web.sparkAdminControls({user:{uid:'mock'},admin:{status:'authorized',role:'delegate',command:'',target:'',delegations:[]}}),/Unrecognized requests go to Google Gemini/);
});
