// Firebase client configuration is public, not an authorization boundary.
// The optional test configuration must be supplied at build time and always
// targets a project other than the existing Chatbox project. Rules still apply.
const productionConfig = Object.freeze({
  apiKey: 'AIzaSyAFc4L8Mo8oI8yN2jgQsVSti2xiYJbDTYU',
  authDomain: 'chatbox-afb8c.firebaseapp.com',
  projectId: 'chatbox-afb8c',
  storageBucket: 'chatbox-afb8c.firebasestorage.app',
  messagingSenderId: '1030482946047',
  appId: '1:1030482946047:web:4685bcc689904a658229ca',
  measurementId: 'G-F4661XK99F',
});

export function resolveFirebaseConfig(env = {}) {
  const raw = env.VITE_CHATBOX_TEST_FIREBASE_CONFIG;
  const testMode = env.VITE_CHATBOX_TEST_MODE === 'true';
  if (!testMode) {
    if (raw !== undefined && raw !== '') throw new Error('Test Firebase configuration requires test mode.');
    return {config: productionConfig, isTestProject: false};
  }
  if (typeof raw !== 'string' || !raw.trim()) throw new Error('Test Firebase configuration is required in test mode.');
  let config;
  try { config = JSON.parse(raw); } catch { throw new Error('Invalid test Firebase configuration.'); }
  if (!config || typeof config !== 'object' || Array.isArray(config)) throw new Error('Invalid test Firebase configuration.');
  const {projectId, apiKey, authDomain, messagingSenderId, appId} = config;
  if (typeof projectId !== 'string' || !/^[a-z][a-z0-9-]{4,28}[a-z0-9]$/.test(projectId) || projectId === productionConfig.projectId) {
    throw new Error('A separate test Firebase project is required.');
  }
  if (typeof apiKey !== 'string' || !apiKey.trim() || authDomain !== `${projectId}.firebaseapp.com`
    || typeof messagingSenderId !== 'string' || !/^\d+$/.test(messagingSenderId)
    || typeof appId !== 'string' || !appId.startsWith(`1:${messagingSenderId}:web:`) || !/^1:\d+:web:[a-f0-9]+$/.test(appId)) {
    throw new Error('Incomplete or inconsistent test Firebase web configuration.');
  }
  // Do not pass unrelated endpoints from arbitrary input to the SDK.
  return {config: {projectId, apiKey, authDomain, messagingSenderId, appId}, isTestProject: true};
}
