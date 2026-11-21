// Experimental controls; server-side rules are the actual authorization boundary.
export function blockControls(block, peerUid, busy) {
  if (!block || !peerUid) return '';
  if (block.status === 'error') return `<div class="live-notice">${block.error ? 'Blocked-user status could not be confirmed. ' : ''}<button type="button" data-action="block-retry">Reconnect blocking</button></div>`;
  if (block.status !== 'ready') return `<div class="live-notice" role="status">${block.status === 'saving' ? 'Waiting for the server to confirm the block change. Do not rely on it yet.' : 'Checking blocked-user status…'}</div>`;
  const blocked=block.ids.includes(peerUid);
  return `<div class="live-notice">${blocked ? 'You have blocked this account. Existing history remains visible.' : 'Experimental blocking. Blocking closes this conversation but may not stop calls on other devices.'} <button type="button" data-action="${blocked ? 'block-remove' : 'block-add'}" ${busy ? 'disabled' : ''}>${blocked ? 'Unblock account' : 'Block account and close chat'}</button></div>`;
}
