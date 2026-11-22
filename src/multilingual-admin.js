import { parseSparkCommand } from './spark-admin-commands.js';

// The model is never trusted to execute code or choose a Firestore path. Only
// exact, bounded allowlisted operations are translated to existing commands.
export function parseMultilingualAdminProposal(response) {
  if (typeof response !== 'string' || response.length > 2048) return null;
  let data;
  try { data = JSON.parse(response); } catch { return null; }
  if (!data || typeof data !== 'object' || Array.isArray(data)) return null;
  const keys = Object.keys(data).sort();
  if (keys.length !== 2 || keys[0] !== 'action' || keys[1] !== 'value' || typeof data.value !== 'string') return null;
  let command;
  if (data.action === 'set_welcome' && data.value === data.value.trim() && data.value.length > 0 && data.value.length <= 160) command = `welcome: ${data.value}`;
  else if (data.action === 'set_theme' && ['default','lavender'].includes(data.value)) command = `theme: ${data.value}`;
  else if (data.action === 'set_menu' && ['top','bottom'].includes(data.value)) command = `menu: ${data.value}`;
  else return null;
  const proposal = parseSparkCommand(command);
  if (!proposal || proposal.action !== data.action || proposal.value !== data.value) return null;
  return {proposal, command};
}
