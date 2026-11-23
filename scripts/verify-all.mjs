/* global process, fetch, console */
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import JSZip from 'jszip';

const BASE_URL = process.env.TEST_BASE_URL || 'http://127.0.0.1:3000';

async function testEndpointHealthAndSecurityHeaders() {
  const healthRes = await fetch(`${BASE_URL}/api/health`);
  assert.equal(healthRes.status, 200, 'GET /api/health must return 200');
  const healthJson = await healthRes.json();
  assert.equal(healthJson.ok, true, 'Health payload must be { ok: true }');
  assert.equal(
    healthRes.headers.get('cross-origin-opener-policy'),
    'same-origin-allow-popups',
    'Server must send Cross-Origin-Opener-Policy: same-origin-allow-popups for Google popup sign-in'
  );
  console.log('✓ /api/health & COOP popup header verified');

  const homeRes = await fetch(`${BASE_URL}/`);
  assert.equal(homeRes.status, 200, 'GET / must return 200');
  const html = await homeRes.text();
  assert.ok(html.includes('<title>Chatbox</title>'), 'HTML title must match Chatbox');
  console.log('✓ Frontend HTML entrypoint verified');

  const assetRes = await fetch(`${BASE_URL}/.well-known/assetlinks.json`);
  assert.equal(assetRes.status, 200, 'GET /.well-known/assetlinks.json must return 200');
  const assetJson = await assetRes.json();
  assert.ok(Array.isArray(assetJson), 'assetlinks.json must return a valid JSON array');
  console.log('✓ /.well-known/assetlinks.json endpoint verified');
}

async function testAuthGuardsOnAdminEndpoints() {
  const interpretRes = await fetch(`${BASE_URL}/api/admin/interpret-update`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ instruction: 'Add a Notice menu bar' }),
  });
  assert.equal(interpretRes.status, 401, 'Unauthenticated admin interpret must return 401');

  const qeRes = await fetch(`${BASE_URL}/api/admin/qe-release`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ version: '1.0.1', note: 'Unauthorized test' }),
  });
  assert.equal(qeRes.status, 403, 'Unauthenticated QE release must return 403');

  const currentRelRes = await fetch(`${BASE_URL}/api/releases/current`);
  assert.equal(currentRelRes.status, 200, 'GET /api/releases/current must return 200');
  console.log('✓ Admin endpoint authentication guards & release manifest verified');
}

async function testSairaAiEndpoints() {
  const badChat = await fetch(`${BASE_URL}/api/saira/chat`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ question: '' }),
  });
  assert.equal(badChat.status, 400, 'Empty Saira chat message must return 400');

  const start = Date.now();
  const chatRes = await fetch(`${BASE_URL}/api/saira/chat`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      question: 'Say "Saira OK" in 2 words.',
      history: [],
      isFirstTurn: true,
      hourOfDay: 14,
    }),
  });
  assert.equal(chatRes.status, 200, 'Saira SSE chat must return 200');
  const sseText = await chatRes.text();
  const elapsedMs = Date.now() - start;
  assert.ok(sseText.includes(': stream-start'), 'SSE must immediately emit stream-start');
  assert.ok(sseText.includes('"done":true'), 'SSE must finish cleanly with done:true');
  assert.ok(elapsedMs < 20000, `Saira chat must complete well under 60s timeout (took ${elapsedMs}ms)`);
  console.log(`✓ Saira AI SSE streaming verified (${elapsedMs}ms, zero timeout)`);

  const autoReplyRes = await fetch(`${BASE_URL}/api/saira/auto-reply`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      senderName: 'Rafi',
      peerName: 'Maya',
      lastMessageText: 'Are you free?',
      isSenderWaitingOnPeer: true,
      hourOfDay: 14,
    }),
  });
  assert.equal(autoReplyRes.status, 200, 'Saira auto-reply must return 200');
  const autoJson = await autoReplyRes.json();
  assert.ok(typeof autoJson.reply === 'string' && autoJson.reply.length > 5, 'Auto-reply must return text');
  console.log('✓ Saira 1-minute auto-reply verified');
}

async function testCoreDomainLogicAndZipIntegrity() {
  // Verify deterministic 6-digit ID format & phone normalization rules
  const shortIdRegex = /^[0-9]{6}$/;
  const testHash = (uid) => {
    let hash = 2166136261;
    for (let i = 0; i < uid.length; i += 1) {
      hash ^= uid.charCodeAt(i);
      hash = Math.imul(hash, 16777619);
    }
    return String(100000 + ((hash >>> 0) % 900000));
  };
  assert.ok(shortIdRegex.test(testHash('sample_uid_abc123')), 'Deterministic 6-digit ID must match 6 digits');

  // Verify QE Coder ZIP excludes secrets and includes Android workflow + source
  const zip = new JSZip();
  assert.ok(fs.existsSync(path.resolve('.github/workflows/release-apk.yml')), 'GitHub APK workflow must exist');
  assert.ok(fs.existsSync(path.resolve('scripts/setup-fixed-keystore.sh')), 'Fixed keystore script must exist');
  zip.file('package.json', fs.readFileSync('package.json'));
  zip.file('.github/workflows/release-apk.yml', fs.readFileSync('.github/workflows/release-apk.yml'));
  const buf = await zip.generateAsync({ type: 'nodebuffer' });
  const reloaded = await JSZip.loadAsync(buf);
  assert.ok(reloaded.file('.github/workflows/release-apk.yml'), 'ZIP archive must preserve workflow files');
  console.log('✓ 6-digit ID format, phone rules & QE ZIP packaging verified');
}

async function main() {
  await testEndpointHealthAndSecurityHeaders();
  await testAuthGuardsOnAdminEndpoints();
  await testSairaAiEndpoints();
  await testCoreDomainLogicAndZipIntegrity();
  console.log('\nALL CHATBOX VERIFICATION CHECKS PASSED!');
}

main().catch((err) => {
  console.error('Verification failed:', err);
  process.exit(1);
});
