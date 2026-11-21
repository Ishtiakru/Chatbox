import { escapeHtml as e } from './knowledge.js';
import { otherParticipant } from './chat-utils.js';
import { LIVE_CHAT_ENABLED, EXPERIMENTAL_CALLS_ENABLED, EXPERIMENTAL_BLOCKING_ENABLED, EXPERIMENTAL_MESSAGE_COOLDOWN, EXPERIMENTAL_REQUEST_COOLDOWN } from './feature-flags.js';
import { experimentalCallPanel } from './call-view.js';
import { blockControls } from './block-view.js';
import { peerProfileHeading } from './peer-profile-view.js';
import { liveIdCard } from './live-id-view.js';
const icon = name => `<i data-lucide="${name}"></i>`;

function conversationControls(conversation, uid, busy, canInteract) {
  if (conversation.pendingWrites) return '<div class="live-notice">Updating request…</div>';
  if (conversation.status === 'active') return `<button class="account-button" data-action="live-close" ${busy ? 'disabled' : ''}>Close conversation</button>`;
  if (conversation.status === 'pending' && conversation.createdBy === uid) return `<div class="live-notice">Waiting for the other person to accept. Messaging and calls are unavailable until then.</div><button class="account-button" data-action="live-close" ${busy ? 'disabled' : ''}>Cancel request</button>`;
  if (conversation.status === 'pending') return `<div class="live-notice">This person requested a conversation. Accept only if you trust them.</div><div class="request-actions"><button class="primary-button" data-action="live-accept" ${busy || !canInteract ? 'disabled' : ''}>Accept request</button><button class="account-button" data-action="live-close" ${busy ? 'disabled' : ''}>Decline</button></div>`;
  return '<div class="live-notice">This conversation is closed or unavailable. Previous messages remain visible. Closing does not block new requests.</div>';
}

function avatar(uid, extra = '') {
  return `<span class="messenger-avatar ${extra}" aria-hidden="true">${e((uid || '?').slice(0, 1).toUpperCase())}</span>`;
}
function peerName(uid, profile) {
  return profile?.uid === uid && profile.status === 'ready' && typeof profile.name === 'string' && profile.name ? profile.name : uid || 'Chatbox user';
}
function conversationRow(c, uid, selected, profile, profiles) {
  const peer = otherParticipant(c.participants, uid);
  const listed = profiles?.get(peer);
  const selectedProfile = c.id === selected && profile?.status === 'ready' ? profile : listed;
  const name = peerName(peer, selectedProfile);
  const status = c.status === 'pending' ? (c.createdBy === uid ? 'Request sent · waiting for acceptance' : 'Message request · tap to review') : c.status === 'active' ? 'Conversation' : 'Closed conversation';
  return `<button type="button" class="live-contact ${c.id === selected ? 'selected' : ''}" data-live-select="${e(c.id)}">${avatar(name)}<span><strong><bdi>${e(name)}</bdi></strong><small>${e(status)}</small></span>${icon('arrow-right')}</button>`;
}

export function liveView(state) {
  const live = state.live;
  let body;
  if (!LIVE_CHAT_ENABLED) {
    body = `<div class="live-empty"><div class="lock-icon">${icon('lock-keyhole')}</div><h3>Live chat is being prepared.</h3><p>This feature is switched off until Firestore rules are reviewed, deployed and tested. The current messages are still a local demo.</p><button class="primary-button" data-view="chats">Explore the demo ${icon('arrow-right')}</button></div>`;
  } else if (!state.authReady) {
    body = '<div class="live-empty"><h3>Checking your account…</h3></div>';
  } else if (!state.user) {
    body = `<div class="live-empty"><div class="lock-icon">${icon('users')}</div><h3>Sign in to connect.</h3><p>Live conversations require your Google account and a configured Firestore database.</p><button class="google-button" data-action="sign-in" ${state.authLoading ? 'disabled' : ''}>${state.authLoading ? 'Signing in…' : 'Sign in with Google'}</button></div>`;
  } else {
    const active = live.conversations.find(c => c.id === live.selected);
    const peerUid = active ? otherParticipant(active.participants, state.user.uid) : '';
    const canInteract = !EXPERIMENTAL_BLOCKING_ENABLED || (!!peerUid && live.block?.status === 'ready' && !live.block.ids.includes(peerUid));
    const busy = live.busy || !live.ready;
    const disabled = busy || active?.status !== 'active' || active?.pendingWrites || !canInteract;
    const requests = live.conversations.filter(c => c.status === 'pending' && c.createdBy !== state.user.uid);
    const conversations = live.conversations.filter(c => !requests.includes(c));
    const renderRows = items => items.map(c => conversationRow(c, state.user.uid, live.selected, live.peerProfile, live.conversationProfiles)).join('');
    const warnings = `${live.pendingNotice ? '<div class="live-notice" role="status">Still waiting for the service. The outcome is unknown; check the conversation after reconnecting before trying again.</div>' : ''}${live.error ? `<div class="live-error" role="alert">${e(live.error)}${!live.ready && !live.connecting ? ' <button type="button" data-action="live-retry">Reconnect</button>' : ''}</div>` : ''}`;
    const details = active ? `<section class="messenger-details"><header class="messenger-details-top"><button class="messenger-icon" type="button" data-action="live-thread" aria-label="Back to conversation">${icon('arrow-left')}</button></header><div class="messenger-details-profile">${avatar(peerName(peerUid, live.peerProfile), 'large')}<h2><bdi>${e(peerName(peerUid, live.peerProfile))}</bdi></h2><p>Display names are not verified</p></div><div class="messenger-details-section"><h3>Conversation</h3>${peerProfileHeading(peerUid, live.peerProfile)}<div class="messenger-details-actions">${conversationControls(active, state.user.uid, busy, canInteract)}</div></div><div class="messenger-details-section"><h3>Privacy &amp; support</h3><p>Messages are not end-to-end encrypted. Closing a conversation does not block future requests.</p>${EXPERIMENTAL_BLOCKING_ENABLED ? blockControls(live.block, peerUid, busy || active.pendingWrites) : '<p>Blocking is not available in this build.</p>'}</div></section>` : '';
    const thread = active ? `<div class="messenger-thread-head"><button class="messenger-icon messenger-back" type="button" data-action="live-list" aria-label="Back to messages">${icon('arrow-left')}</button>${avatar(peerName(peerUid, live.peerProfile), 'small')}<div class="messenger-thread-name"><strong><bdi>${e(peerName(peerUid, live.peerProfile))}</bdi></strong><small>${active.status === 'active' ? 'Direct conversation' : active.status === 'pending' ? 'Message request' : 'Closed conversation'}</small></div><button class="messenger-icon" type="button" data-action="live-details" aria-label="Conversation details">${icon('settings')}</button></div>${active.status !== 'active' || active.pendingWrites ? `<div class="messenger-request-controls">${conversationControls(active, state.user.uid, busy, canInteract)}</div>` : ''}${EXPERIMENTAL_BLOCKING_ENABLED ? blockControls(live.block, peerUid, busy || active.pendingWrites) : ''}${active.status === 'active' && !active.pendingWrites && canInteract && EXPERIMENTAL_CALLS_ENABLED && live.call ? experimentalCallPanel(live.call) : ''}<div class="live-messages" id="live-messages">${live.messagesFromCache ? '<div class="live-hint" role="status">Messages may be incomplete or out of date while awaiting a server update.</div>' : ''}${live.historyReset ? `<div class="live-hint" role="status">Recent messages refreshed.${live.hasOlder ? ' Use Load earlier messages to retrieve older history.' : ''}</div>` : ''}${live.hasOlder ? `<button type="button" class="load-older" data-action="load-older-messages" ${live.loadingOlder ? 'disabled' : ''}>${live.loadingOlder ? 'Loading…' : 'Load earlier messages'}</button>` : ''}${live.messages.length ? live.messages.map(m => `<div class="live-message ${m.senderId === state.user.uid ? 'own' : ''}"><div>${e(m.body || '').replaceAll('\n','<br>')}</div><small>${!m.pendingWrites && m.sentAt?.toDate ? e(new Intl.DateTimeFormat('en',{hour:'numeric',minute:'2-digit'}).format(m.sentAt.toDate())) : 'Sending…'}</small></div>`).join('') : `<div class="live-hint">${live.messagesFromCache ? 'Waiting for a server update.' : active.status === 'active' ? 'No messages yet. Say hello!' : 'No messages to show.'}</div>`}</div><form id="live-message-form" class="composer live-composer"><input id="live-message-input" maxlength="2000" value="${e(live.messageDraft)}" placeholder="Message" aria-label="Live message" ${disabled ? 'disabled' : ''}><button type="submit" class="send-button" aria-label="Send live message" ${disabled ? 'disabled' : ''}>${icon('arrow-up')}</button></form>` : '<div class="live-hint messenger-select">Choose a conversation or start a new one.</div>';
    body = `${warnings}<div class="live-layout"><div class="live-list"><header class="messenger-list-head"><div><span class="messenger-kicker">CHATBOX · EXPERIMENTAL</span><h2>Messages</h2></div><button class="messenger-icon" type="button" data-action="live-new-toggle" aria-label="${live.newOpen ? 'Close new conversation' : 'New conversation'}" aria-expanded="${!!live.newOpen}">${icon(live.newOpen ? 'x' : 'square-pen')}</button></header><div class="messenger-intro">Your space for real conversations · Names are unverified</div><details class="messenger-privacy"><summary>About your privacy</summary><p>Experimental chat is not end-to-end encrypted. Do not use for sensitive conversations. Drafts stay in memory and are cleared on refresh, sign-out or session reset. Signing out or refreshing may not cancel a pending action; check its result before retrying.${EXPERIMENTAL_MESSAGE_COOLDOWN ? ' Message cooldown: one per account every two seconds.' : ''}${EXPERIMENTAL_REQUEST_COOLDOWN ? ' Request cooldown: one per account every 30 seconds.' : ''}</p></details>${live.newOpen ? `<div class="messenger-new">${liveIdCard(state.user.uid, live.idCopyStatus)}<form id="live-new-form"><label for="live-peer-input">Request a conversation</label><p>Enter the other person's Chatbox ID. They must sign in to Live chats first.</p><input id="live-peer-input" maxlength="128" value="${e(live.peerDraft)}" placeholder="Their Chatbox ID" required><button class="primary-button" type="submit" ${busy || (EXPERIMENTAL_BLOCKING_ENABLED && live.block?.status !== 'ready') ? 'disabled' : ''}>${live.busy ? 'Please wait…' : 'Send request'} ${icon('arrow-right')}</button></form></div>` : ''}${requests.length ? `<div class="messenger-request-heading">${icon('message-circle')} <strong>Message requests</strong><span>${requests.length}</span></div>${renderRows(requests)}` : ''}<div class="messenger-list-label">CHATS</div><div class="live-list-items">${renderRows(conversations) || `<p class="live-hint">${live.ready ? 'No conversations yet. Tap the new message button to connect.' : 'Connecting to Firestore…'}</p>`}</div></div><div class="live-thread">${live.screen === 'details' && active ? details : thread}</div></div>`;
  }
  const signedIn = LIVE_CHAT_ENABLED && state.authReady && state.user;
  const screen = signedIn && live.selected ? live.screen || 'list' : 'list';
  return `<main class="main feature-page live-page ${signedIn ? `messenger-page messenger-screen-${e(screen)}` : ''}">${signedIn ? '' : `<div class="feature-top"><div><span class="eyebrow">A SPACE FOR REAL CONVERSATIONS</span><h2>Live chats <span class="beta-pill">EXPERIMENTAL</span></h2><p>Separate from your local demo conversations.</p></div><div class="feature-icon">${icon('message-circle')}</div></div>`}${body}</main>`;
}
