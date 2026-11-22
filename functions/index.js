import { readFileSync } from 'node:fs';
import { initializeApp } from 'firebase-admin/app';
import { getAuth } from 'firebase-admin/auth';
import { getFirestore, FieldValue } from 'firebase-admin/firestore';
import { onCall, HttpsError } from 'firebase-functions/v2/https';
import { defineSecret } from 'firebase-functions/params';
import { cleanInput, parseProposal, isAdministrator, limitAnswer } from './logic.js';
import { updateWelcome, changeAdministrator, OperationError } from './admin-operations.js';
import { askProvider } from './provider.js';

initializeApp();
const db = getFirestore();
const aiKey = defineSecret('SAIRA_AI_API_KEY');
const knowledge = JSON.parse(readFileSync(new URL('./knowledge.generated.json', import.meta.url), 'utf8'));
const adminRef = db.doc('system/admin');
const options = { region: 'us-central1', enforceAppCheck: true, timeoutSeconds: 30, maxInstances: 4 };
const aiOptions = { ...options, secrets: [aiKey] };

function requireUser(request) {
  if (!request.auth?.uid) throw new HttpsError('unauthenticated', 'Sign in first.');
  return request.auth.uid;
}
function checkedText(value, max = 1000) {
  try { return cleanInput(value, max); } catch { throw new HttpsError('invalid-argument', 'Invalid input.'); }
}
async function limit(uid, kind) {
  const ref = db.doc(`rateLimits/${uid}_${kind}`);
  await db.runTransaction(async tx => {
    const snap = await tx.get(ref);
    const now = Date.now();
    if (snap.exists && (snap.data().nextAllowedMs || 0) > now) throw new HttpsError('resource-exhausted', 'Please wait a few seconds.');
    tx.set(ref, { nextAllowedMs: now + 10000 });
  });
}
async function requireAdmin(uid) {
  if (!isAdministrator((await adminRef.get()).data(), uid)) throw new HttpsError('permission-denied', 'Administrator access required.');
}
async function provider(instructions, input) {
  try { return await askProvider({ key: aiKey.value(), instructions, input }); }
  catch { throw new HttpsError('unavailable', 'AI service is unavailable or not configured.'); }
}

export const adminStatus = onCall(options, async request => {
  const uid = requireUser(request);
  return { isAdmin: isAdministrator((await adminRef.get()).data(), uid) };
});

// Review-only source handover endpoint. The Drive file itself must separately
// enforce its own permissions: hiding a URL or a QR is not access control.
// Configuration is written only by a trusted operator via the Admin SDK.
export const getSourceHandover = onCall(options, async request => {
  const uid = requireUser(request);
  await requireAdmin(uid);
  const data = (await db.doc('system/sourceHandover').get()).data();
  let url;
  try { url = new URL(data?.url); } catch { throw new HttpsError('failed-precondition', 'Source handover is not configured.'); }
  if (url.protocol !== 'https:' || url.hostname !== 'drive.google.com' || url.username || url.password || !/^\/file\/d\/[A-Za-z0-9_-]+\/view$/.test(url.pathname)) {
    throw new HttpsError('failed-precondition', 'Invalid source handover configuration.');
  }
  const commit = typeof data?.commit === 'string' && /^[a-f0-9]{40}$/.test(data.commit) ? data.commit : '';
  return { url: url.href, commit };
});

// A user explicitly sends only the question to the AI provider, not their other chats.
export const askSaira = onCall(aiOptions, async request => {
  const uid = requireUser(request);
  const question = checkedText(request.data?.question);
  await limit(uid, 'saira');
  const answer = await provider(`You are Saira, a helpful app guide. Answer in the user's language. Only claim facts grounded in this curated app knowledge. If the knowledge is incomplete or may be outdated, say so. Do not claim planned features are available. Knowledge version ${knowledge.version}:\n${knowledge.content}`, question);
  return { answer: limitAnswer(answer), knowledgeVersion: knowledge.version, knowledgeHash: knowledge.sha256 };
});

// Natural-language interpretation does not mutate anything and the only supported operation is set_welcome.
export const previewAdminUpdate = onCall(aiOptions, async request => {
  const uid = requireUser(request);
  await requireAdmin(uid);
  const command = checkedText(request.data?.command);
  await limit(uid, 'admin');
  const text = await provider('Interpret the admin request as JSON only. Allowed outputs: {"action":"set_welcome","value":"plain text up to 160 characters"} or {"action":"unsupported"}. Choose unsupported for anything other than setting a welcome message. Do not execute or suggest code.', command);
  return { proposal: parseProposal(text) };
});

// Authoritative admin status is rechecked in the same transaction as every privileged write.
export const applyAdminUpdate = onCall(options, async request => {
  const uid = requireUser(request);
  try {
    await updateWelcome(db, uid, request.data?.proposal, request.data?.confirmed, () => FieldValue.serverTimestamp());
  } catch (error) {
    if (error instanceof OperationError) throw new HttpsError(error.code, error.message);
    throw error;
  }
  return { applied: true };
});

export const transferAdmin = onCall(options, async request => {
  const uid = requireUser(request);
  const targetUid = checkedText(request.data?.targetUid, 128);
  const authTime = request.auth?.token?.auth_time;
  if (typeof authTime !== 'number' || Date.now() / 1000 - authTime > 300 || authTime > Date.now() / 1000 + 60) throw new HttpsError('failed-precondition', 'Please sign in again before transferring admin access.');
  if (targetUid === uid || request.data?.confirmed !== true) throw new HttpsError('failed-precondition', 'Confirm a different administrator.');
  // Check authorization before querying another account, and recheck atomically on write.
  await requireAdmin(uid);
  let target;
  try { target = await getAuth().getUser(targetUid); } catch { throw new HttpsError('invalid-argument', 'Target account not found.'); }
  try {
    await changeAdministrator(db, uid, target, request.data?.confirmed, () => FieldValue.serverTimestamp());
  } catch (error) {
    if (error instanceof OperationError) throw new HttpsError(error.code, error.message);
    throw error;
  }
  return { transferred: true };
});
