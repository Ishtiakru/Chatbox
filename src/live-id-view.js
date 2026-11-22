import {escapeHtml as e} from './knowledge.js';

export function liveIdFeedback(copyStatus = '') {
  return copyStatus === 'copied'
    ? 'Copied. Share only with someone you trust; other apps may read your clipboard.'
    : copyStatus === 'unavailable'
      ? 'Copy unavailable. Press and hold your ID to select it.'
      : '';
}

// Only the current user's ID is shown; copy is an explicit user action.
export function liveIdCard(uid, copyStatus = '') {
  return `<div class="live-id"><span>YOUR CHATBOX ID</span><code>${e(uid)}</code><button type="button" class="live-id-copy" data-action="live-copy-id">Copy ID</button><small>Share this ID privately with someone you trust.</small><small class="live-id-feedback" role="status" aria-live="polite">${liveIdFeedback(copyStatus)}</small></div>`;
}
