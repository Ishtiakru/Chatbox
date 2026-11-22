// Build-time gates are not security boundaries; backend authorization remains mandatory.
export const LIVE_CHAT_ENABLED = import.meta.env.VITE_ENABLE_FIRESTORE_CHAT === 'true';
// Separate, review-only 2-second per-user message cooldown lab. Requires its
// matching rules; enabling only the client will make sends fail.
export const EXPERIMENTAL_MESSAGE_COOLDOWN = LIVE_CHAT_ENABLED && import.meta.env.VITE_ENABLE_EXPERIMENTAL_MESSAGE_COOLDOWN === 'true';
// The request cooldown candidate includes the message cooldown policy too.
export const EXPERIMENTAL_REQUEST_COOLDOWN = EXPERIMENTAL_MESSAGE_COOLDOWN && import.meta.env.VITE_ENABLE_EXPERIMENTAL_REQUEST_COOLDOWN === 'true';
// Browser emulator/lab only; cannot enable calling independently of live chat.
export const EXPERIMENTAL_CALLS_ENABLED = LIVE_CHAT_ENABLED && import.meta.env.VITE_ENABLE_EXPERIMENTAL_CALLS === 'true';
// Optional public STUN server for the browser lab only; it is not a TURN relay.
export const EXPERIMENTAL_STUN_ENABLED = EXPERIMENTAL_CALLS_ENABLED && import.meta.env.VITE_ENABLE_EXPERIMENTAL_STUN === 'true';
// Blocking remains an isolated lab: disabled unless explicitly enabled alongside chat.
export const EXPERIMENTAL_BLOCKING_ENABLED = LIVE_CHAT_ENABLED && import.meta.env.VITE_ENABLE_EXPERIMENTAL_BLOCKING === 'true';
export const SAIRA_PROVIDER = import.meta.env.VITE_SAIRA_PROVIDER === 'firebase-ai' ? 'firebase-ai' : 'functions';
export const SAIRA_AI_ENABLED = import.meta.env.VITE_ENABLE_SAIRA_AI === 'true';
export const ANDROID_AI_LAB_ENABLED = SAIRA_AI_ENABLED && import.meta.env.VITE_CHATBOX_TEST_MODE === 'true' && import.meta.env.VITE_CHATBOX_NATIVE_TEST_MODE === 'true' && import.meta.env.VITE_ENABLE_EXPERIMENTAL_ANDROID_AI === 'true';
export const SPARK_ADMIN_ENABLED = import.meta.env.VITE_ENABLE_SPARK_ADMIN === 'true';
export const ADMIN_BACKEND_ENABLED = import.meta.env.VITE_ENABLE_ADMIN_BACKEND === 'true';
// Review-only. Requires a deployed, authorized backend and a private Drive file.
export const SOURCE_HANDOVER_ENABLED = ADMIN_BACKEND_ENABLED && import.meta.env.VITE_ENABLE_SOURCE_HANDOVER === 'true';
export const APP_CHECK_SITE_KEY = import.meta.env.VITE_APP_CHECK_SITE_KEY || '';
