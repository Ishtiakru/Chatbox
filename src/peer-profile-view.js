import { escapeHtml as e } from './knowledge.js';

// Profile display names are user-controlled and must never be presented as a
// verified identity. Retain the exact ID as the more useful comparison value.
export function peerProfileHeading(uid, profile) {
  const visible = profile?.uid === uid && profile.status === 'ready' && typeof profile.name === 'string' && profile.name;
  return `<div><strong>${visible ? `<bdi>${e(profile.name)}</bdi>` : 'Direct conversation'}</strong>${visible ? '<span>Display name is user-provided, not verified.</span>' : ''}<span>Chatbox ID: <bdi>${e(uid || '')}</bdi></span></div>`;
}
