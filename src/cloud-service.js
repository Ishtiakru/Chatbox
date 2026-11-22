import { getFunctions, httpsCallable } from 'firebase/functions';
import { app } from './firebase.js';
import { ensureAppCheck } from './app-check.js';

const allowed = new Set(['askSaira', 'adminStatus', 'previewAdminUpdate', 'applyAdminUpdate', 'transferAdmin', 'getSourceHandover']);
export async function callCloud(name, data = {}) {
  if (!allowed.has(name)) throw new Error('Unsupported request.');
  ensureAppCheck();
  const result = await httpsCallable(getFunctions(app, 'us-central1'), name)(data);
  return result.data;
}
export async function watchPublicWelcome(next, fail) {
  const { getFirestore, doc, onSnapshot } = await import('firebase/firestore');
  return onSnapshot(doc(getFirestore(app), 'settings', 'public'), snap => {
    const welcome = snap.data()?.welcome;
    next(typeof welcome === 'string' && welcome.length <= 160 ? welcome : '');
  }, fail);
}
