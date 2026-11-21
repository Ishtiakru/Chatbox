import './style.css';
import { createIcons, MessageCircle, Phone, Shield, CircleHelp, SquarePen, Search, ArrowLeft, Video, MoreHorizontal, Sparkles, CheckCheck, Plus, Smile, ArrowUp, LockKeyhole, Ellipsis, ArrowUpRight, PhoneCall, ArrowRight, ShieldCheck, Lock, Info, Users, History, X } from 'lucide';
const icons = { MessageCircle, Phone, Shield, CircleHelp, SquarePen, Search, ArrowLeft, Video, MoreHorizontal, Sparkles, CheckCheck, Plus, Smile, ArrowUp, LockKeyhole, Ellipsis, ArrowUpRight, PhoneCall, ArrowRight, ShieldCheck, Lock, Info, Users, History, X };
import { sairaReply, escapeHtml as e, APP_VERSION } from './knowledge.js';
import { watchAuth, isCurrentAuthUser, googleSignIn, googleSignOut, googleReauthenticate } from './firebase.js';
import { resolveFirebaseConfig } from './firebase-config.js';
import { authErrorMessage } from './auth-errors.js';
import { LIVE_CHAT_ENABLED, EXPERIMENTAL_CALLS_ENABLED, EXPERIMENTAL_BLOCKING_ENABLED, EXPERIMENTAL_MESSAGE_COOLDOWN, EXPERIMENTAL_REQUEST_COOLDOWN, SAIRA_AI_ENABLED, SAIRA_PROVIDER, SPARK_ADMIN_ENABLED, ADMIN_BACKEND_ENABLED, APP_CHECK_SITE_KEY } from './feature-flags.js';
import { sairaAiView, adminControls, sparkAdminControls } from './cloud-views.js';
import { chatErrorMessage } from './chat-utils.js';
import { liveView } from './live-view.js';
import { disposeLiveResources } from './live-lifecycle.js';
import { createPeerProfileController } from './peer-profile-controller.js';
import { createSlowOperationNotice } from './slow-operation-notice.js';
import { createLiveDrafts } from './live-drafts.js';
import { createAccountOperationGuard } from './account-operation-guard.js';
import { canUseCallPage } from './call-page-lifecycle.js';

const contacts = [
  { id: 'saira', name: 'Saira', role: 'Your app companion', avatar: '✳', color: 'saira', label: 'AI GUIDE', online: true },
  { id: 'maya', name: 'Maya Rahman', role: 'Demo conversation', avatar: 'MR', color: 'peach', online: true },
  { id: 'arif', name: 'Arif Hasan', role: 'Demo conversation', avatar: 'AH', color: 'lavender', online: false },
  { id: 'nora', name: 'Nora Ahmed', role: 'Demo conversation', avatar: 'NA', color: 'yellow', online: false },
  { id: 'together', name: 'The good people', role: 'Demo group', avatar: '✳', color: 'blue', online: false },
];
const seeds = {
  saira: [{ from: 'them', text: 'হ্যালো! আমি Saira ✨ এই অ্যাপ সম্পর্কে কিছু জানতে চাইলে আমাকে জিজ্ঞেস করুন। আমি এখন একটি অফলাইন গাইড।', time: 'এখন' }],
  maya: [
    { from: 'them', text: 'Hey! How has your week been going? ☀️', time: '10:24 AM' },
    { from: 'me', text: 'Pretty good, actually! Just taking things one day at a time.', time: '10:26 AM' },
    { from: 'them', text: 'Love that for you. We should catch up soon!', time: '10:28 AM' },
    { from: 'them', text: 'Maybe a little coffee date this weekend? ☕', time: '10:28 AM' },
    { from: 'me', text: 'That sounds like the perfect plan 🤍', time: '10:30 AM' },
  ],
  arif: [{ from: 'them', text: 'Let’s make something great together ✨', time: 'Yesterday' }],
  nora: [{ from: 'them', text: 'Sending you a little sunshine today!', time: 'Yesterday' }],
  together: [{ from: 'them', text: 'A little space for our favorite people 💛', time: 'Monday' }],
};
const key = 'saira-demo-messages-v1';
let saved = {};
try { const parsed = JSON.parse(localStorage.getItem(key) || '{}'); if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) saved = parsed; } catch { /* ignore invalid local data */ }
const state = { view: 'chats', active: 'maya', search: '', draft: '', saved, modal: null, mobileChat: false, user: null, authReady: false, authLoading: false, authError: '', live: { ready: false, error: '', conversations: [], selected: null, messages: [], hasOlder: false, loadingOlder: false, messagesFromCache: true, historyReset: false, peerProfile:null, peerDraft: '', messageDraft: '', busy: false }, ai: { draft: '', messages: [], busy: false, error: '' }, admin: { status: 'idle', error: '', notice: '', command: '', proposal: null, target: '', confirm: false, busy: false }, publicWelcome: '' };
const root = document.querySelector('#app');
const testProject = resolveFirebaseConfig(import.meta.env);
document.documentElement.classList.toggle('test-firebase-project', testProject.isTestProject);
const liveDrafts = createLiveDrafts();
const slowLiveWrite = createSlowOperationNotice(visible => {
  state.live.pendingNotice = visible;
  if (visible && state.view === 'live') render();
});
const icon = (name, extra = '') => `<i data-lucide="${name}" ${extra}></i>`;
const contact = () => contacts.find(c => c.id === state.active) || contacts[0];
const messages = id => [...(seeds[id] || []), ...(Array.isArray(state.saved[id]) ? state.saved[id].filter(m => m && typeof m.text === 'string' && (m.from === 'me' || m.from === 'them')) : [])];
const avatar = (c, size = '') => `<span class="avatar ${e(c.color)} ${size}" aria-hidden="true">${e(c.avatar)}</span>`;
const navButton = (id, iconName, text) => `<button class="rail-button ${state.view === id ? 'active' : ''}" data-view="${id}" aria-label="${text}" title="${text}">${icon(iconName)}<span>${text}</span></button>`;
function sidebar() {
  const list = contacts.filter(c => `${c.name} ${c.role}`.toLowerCase().includes(state.search.toLowerCase()));
  return `<aside class="conversations ${state.mobileChat && state.view === 'chats' ? 'mobile-hidden' : ''}">
    <div class="conversations-top"><div class="heading-group"><img class="mobile-brand" src="/chatbox-logo.jpg" alt="Chatbox logo"><div><span class="eyebrow">YOUR SPACE</span><h1>Messages <span class="heading-spark">✳</span></h1></div></div><button class="square-button" data-action="new" aria-label="New message">${icon('square-pen')}</button></div>
    ${state.publicWelcome ? `<p class="public-welcome">${e(state.publicWelcome)}</p>` : ''}
    <div class="search-wrap">${icon('search')}<input id="search" type="search" placeholder="Search conversations" value="${e(state.search)}" aria-label="Search conversations"><span class="search-shortcut">⌘ K</span></div>
    <div class="list-heading"><span>YOUR CONVERSATIONS</span><span>${String(list.length).padStart(2,'0')}</span></div>
    <div class="contact-list">${list.length ? list.map(c => {
      const last = messages(c.id).at(-1);
      return `<button class="contact-row ${state.view === 'chats' && state.active === c.id ? 'selected' : ''}" data-contact="${c.id}"><span class="avatar-wrap">${avatar(c)}${c.online ? '<span class="online-dot"></span>' : ''}</span><span class="contact-copy"><span class="contact-name">${e(c.name)} ${c.id === 'saira' ? '<span class="mini-spark">✦</span>' : ''}</span><span class="contact-preview">${e(last?.text || c.role)}</span></span><span class="contact-meta">${c.id === 'saira' ? '<span class="saira-tag">GUIDE</span>' : '<span>DEMO</span>'}</span></button>`;
    }).join('') : '<p class="no-results">No conversations found.</p>'}</div>
    <div class="sidebar-bottom"><div class="demo-avatar">${e((state.user?.displayName || 'Y').charAt(0).toUpperCase())}</div><div class="account-copy"><strong>${e(state.user?.displayName || 'Your space')}</strong><span>${e(state.user?.email || (state.authReady ? 'Demo mode' : 'Checking sign-in…'))}</span></div><button class="account-button" data-action="${state.user ? 'sign-out' : 'sign-in'}" ${!state.authReady || state.authLoading ? 'disabled' : ''}>${state.authLoading ? 'Please wait' : state.user ? 'Sign out' : 'Sign in'}</button></div>
  </aside>`;
}
function chat() {
  const c = contact();
  return `<main class="main chat-main ${state.mobileChat ? 'mobile-open' : ''}">
    <header class="chat-header"><div class="chat-person"><button class="plain-icon back-button" data-action="back" aria-label="Back to conversations">${icon('arrow-left')}</button><span class="avatar-wrap">${avatar(c, 'small')}${c.online ? '<span class="online-dot"></span>' : ''}</span><div><h2>${e(c.name)} ${c.id === 'saira' ? '<span class="verified">✦</span>' : ''}</h2><p><span class="status-dot"></span>${c.id === 'saira' ? 'Offline app guide' : 'Illustrative conversation'}</p></div></div><div class="header-actions"><button class="action-icon" data-action="voice" aria-label="Voice call">${icon('phone')}</button><button class="action-icon" data-action="video" aria-label="Video call">${icon('video')}</button><span class="header-divider"></span><button class="action-icon" data-action="about" aria-label="Conversation information">${icon('more-horizontal')}</button></div></header>
    <div class="chat-body" id="chat-body"><div class="day-divider"><span></span><b>JUST A LITTLE CLOSER</b><span></span></div><div class="privacy-note">${icon('sparkles')} <span>${c.id === 'saira' ? 'Saira is a simple offline guide, not an AI service yet.' : 'This is a sample conversation. Messages stay on this device.'}</span></div><div class="message-list">${messages(c.id).map((m, i, arr) => `<div class="message ${m.from === 'me' ? 'outgoing' : 'incoming'}">${m.from !== 'me' ? avatar(c, 'tiny') : ''}<div class="message-content"><div class="bubble">${e(m.text).replaceAll('\n','<br>')}</div><div class="message-time">${e(m.time || '')}${m.from === 'me' ? ` ${icon('check-check')}` : ''}</div></div></div>`).join('')}</div></div>
    <div class="composer-area"><form id="message-form" class="composer"><button type="button" class="composer-icon" data-action="attach" aria-label="Attach a file">${icon('plus')}</button><input id="message-input" maxlength="2000" autocomplete="off" placeholder="Write a little something..." value="${e(state.draft)}" aria-label="Write a message"><button type="button" class="composer-icon smile" data-action="emoji" aria-label="Add a smile">${icon('smile')}</button><button type="submit" class="send-button" aria-label="Send message">${icon('arrow-up')}</button></form><div class="composer-foot"><span>${icon('lock-keyhole')} Local demo · not sent to anyone</span><span>Made for meaningful moments <b>✳</b></span></div></div>
  </main>`;
}
function infoPanel() {
  const c = contact();
  return `<aside class="details"><div class="details-heading">A LITTLE MORE <button class="plain-icon" data-action="about" aria-label="About">${icon('ellipsis')}</button></div><div class="profile">${avatar(c, 'large')}<h3>${e(c.name)} ${c.id === 'saira' ? '<span class="verified">✦</span>' : ''}</h3><p>${c.id === 'saira' ? 'Your little guide to this space.' : 'A little demo, just for you.'}</p><span class="profile-status"><span></span> ${c.id === 'saira' ? 'OFFLINE GUIDE' : 'SAMPLE PROFILE'}</span></div><div class="details-rule"></div><div class="detail-section"><div class="detail-title">A NOTE FROM US <span>✳</span></div><p>Good conversations make the world feel a little smaller.</p></div><div class="saira-card"><div class="saira-card-top"><div class="saira-card-icon">✳</div><span>MEET YOUR GUIDE</span></div><h4>Hey, I’m Saira<span> ✦</span></h4><p>Curious about this space? I’m here to help you find your way.</p><button data-contact="saira">Say hello to Saira ${icon('arrow-up-right')}</button></div><div class="details-bottom"><span>DESIGNED FOR CONNECTION</span><div>Made with a little <span>♥</span></div></div></aside>`;
}
function calls() {
 return `<main class="main feature-page"><div class="feature-top"><div><span class="eyebrow">STAY CLOSE, FROM ANYWHERE</span><h2>Calls</h2><p>Some conversations deserve to be heard.</p></div><div class="feature-icon">${icon('phone-call')}</div></div><div class="feature-content"><div class="empty-illustration"><div class="orbit orbit-one"></div><div class="orbit orbit-two"></div><div class="illustration-center">${icon('video')}</div><span class="star-one">✳</span><span class="star-two">✦</span></div><span class="eyebrow">A LITTLE SOMETHING TO LOOK FORWARD TO</span><h3>Closer than a message.</h3><p>Voice and video calling are planned, but not connected yet. Once Firebase and calling services are set up, this space can bring you face to face.</p><button class="primary-button" data-view="chats">Back to conversations ${icon('arrow-right')}</button></div></main>`;
}
function admin() {
 const authorized = state.admin.status === 'authorized' && (SPARK_ADMIN_ENABLED || (ADMIN_BACKEND_ENABLED && APP_CHECK_SITE_KEY));
 return `<main class="main feature-page admin-page"><div class="feature-top"><div><span class="eyebrow">A SPACE FOR THE PEOPLE IN CHARGE</span><h2>Admin studio <span class="beta-pill">EXPERIMENTAL</span></h2><p>A thoughtful way to manage your space.</p></div><div class="feature-icon">${icon('shield-check')}</div></div><div class="admin-layout"><section class="locked-card"><div class="lock-icon">${icon('lock-keyhole')}</div><span class="eyebrow">SECURITY FIRST</span><h3>${authorized ? 'Administrator controls' : 'This space is locked.'}</h3><p>${authorized ? (SPARK_ADMIN_ENABLED ? 'Only limited changes are supported. Full audit history is not available.' : 'Changes are limited, reviewed and recorded.') : 'Verified sign-in and server-side security rules or admin permissions are required.'}</p>${SPARK_ADMIN_ENABLED ? sparkAdminControls(state) : adminControls(state)}<div class="security-note">${icon('shield-check')} An email address alone will never grant admin access.</div></section><section class="studio-preview"><div class="preview-head"><div><span class="eyebrow">WHAT IS SUPPORTED</span><h3>Just say what you need.</h3></div><span class="preview-label">LIMITED</span></div><p>When configured, you can preview and confirm a public welcome message. Code changes and arbitrary actions are not supported.</p><div class="example-command"><span>✳</span><p>“Make the welcome message a little warmer...”</p></div><div class="preview-footer">${icon('info')} AI requests may be sent to an external provider.</div><div class="preview-item">${icon('users')} <div><strong>Admin access</strong><span>One verified admin at a time</span></div>${icon('arrow-up-right')}</div><div class="preview-item">${icon('history')} <div><strong>Activity history</strong><span>${SPARK_ADMIN_ENABLED ? 'Full audit history is not available' : 'Privileged actions are audited'}</span></div>${icon('arrow-up-right')}</div></section></div></main>`;
}
function modal() {
 if (!state.modal) return '';
 const data = {
   authError: ['সাইন-ইন করা যায়নি', state.authError],
   voice: ['Voice calls are on the way', 'Calling is not connected in this prototype. A real call requires authentication, signaling and a calling service.'],
   video: ['A little closer, soon', 'Video calling is not connected yet. We’ll need a secure calling service and camera permissions to make this work.'],
   attach: ['Attachments are coming later', 'Files cannot be uploaded or sent in this local demo.'],
   new: ['Your people, in one place', 'Adding real contacts requires Firebase authentication and a connected database. For now, explore the sample conversations.'],
   about: ['A little note about this space', `Saira is a local prototype (v${APP_VERSION}). The people shown are samples; your messages stay in this browser. Google sign-in is being set up; real messaging, calls, and AI are not connected yet.`],
 }[state.modal];
 if (!data) return '';
 return `<div class="modal-overlay" data-action="close-modal"><section class="modal" role="dialog" aria-modal="true" aria-labelledby="modal-title" data-modal><button class="modal-close" data-action="close-modal" aria-label="Close">${icon('x')}</button><div class="modal-symbol">✳</div><span class="eyebrow">SAIRA / A LITTLE NOTE</span><h2 id="modal-title">${e(data[0])}</h2><p>${e(data[1])}</p><button class="primary-button" data-action="close-modal">Got it ${icon('arrow-right')}</button></section></div>`;
}
function render(scroll = false) {
 const activeId = document.activeElement?.id;
 const selectionStart = document.activeElement?.selectionStart;
 const selectionEnd = document.activeElement?.selectionEnd;
 const liveScrollTop = document.querySelector('#live-messages')?.scrollTop;
 root.innerHTML = `${testProject.isTestProject ? `<div class="test-project-banner" role="status">TEST PROJECT: ${e(testProject.config.projectId)} · Do not use sensitive information</div>` : ''}<div class="app-shell"><nav class="rail"><div class="brand-mark" title="Chatbox"><img src="/chatbox-logo.jpg" alt="Chatbox logo"></div><div class="rail-nav">${navButton('chats','message-circle','Demo')}${navButton('live','users','Live chats')}${navButton('ai','sparkles','Saira AI')}${navButton('calls','phone','Calls')}${navButton('admin','shield','Admin')}</div><div class="rail-bottom"><button class="rail-button" data-action="about" aria-label="About Saira" title="About">${icon('circle-help')}<span>About</span></button><div class="rail-profile">Y</div></div></nav>${sidebar()}${state.view === 'chats' ? chat() + infoPanel() : state.view === 'live' ? liveView(state) : state.view === 'ai' ? sairaAiView(state) : state.view === 'calls' ? calls() : admin()}<nav class="mobile-nav">${navButton('chats','message-circle','Demo')}${navButton('live','users','Live')}${navButton('ai','sparkles','Saira')}${navButton('calls','phone','Calls')}${navButton('admin','shield','Admin')}</nav></div>${modal()}`;
 createIcons({ icons });
 const liveMessages = document.querySelector('#live-messages');
 if (liveMessages && liveScrollTop != null) liveMessages.scrollTop = liveScrollTop;
 // The app rerenders its DOM; reattach current streams without persisting them.
 for (const [id, stream] of [['call-local', state.live.call?.localStream], ['call-remote', state.live.call?.remoteStream]]) {
   const element = document.getElementById(id);
   if (element && stream) { element.srcObject = stream; element.play().catch(() => {}); }
 }
 if (activeId) { const input = document.getElementById(activeId); if (input && !input.disabled) { input.focus(); try { if (selectionStart != null) input.setSelectionRange(selectionStart, selectionEnd); } catch {} } }
 if (scroll) { const el = document.querySelector('#chat-body'); if (el) el.scrollTop = el.scrollHeight; }
}
root.addEventListener('click', event => {
 const el = event.target.closest('button, [data-action]'); if (!el) return;
 if (el.dataset.liveSelect) { selectLiveConversation(el.dataset.liveSelect); return; }
 if (el.dataset.sparkRevoke) { revokeSparkAdmin(el.dataset.sparkRevoke); return; }
 if (el.dataset.action === 'load-older-messages') { state.live.error = ''; messageFeed?.loadOlder(); return; }
 if (el.dataset.callOpen) { try { if (callPageAvailable() && selectedInteractionAllowed()) callController?.openIncoming(el.dataset.callOpen); } catch { /* stale call; listener will refresh */ } return; }
 if (el.dataset.contact) { if (state.view === 'live') stopCallsForPage(); state.active = el.dataset.contact; state.view = 'chats'; state.mobileChat = true; state.draft = ''; render(true); return; }
 if (el.dataset.view) { if (state.view === 'live' && el.dataset.view !== 'live') stopCallsForPage(); state.view = el.dataset.view; state.mobileChat = false; render(true); if (state.view === 'live') resumeCallsForPage(); if (state.view === 'admin' && state.user && (SPARK_ADMIN_ENABLED || (ADMIN_BACKEND_ENABLED && APP_CHECK_SITE_KEY)) && state.admin.status === 'idle') checkAdmin(); return; }
 const action = el.dataset.action;
 if (action === 'call-audio' || action === 'call-video') {
   const selected = state.live.conversations.find(c => c.id === state.live.selected);
   if (callPageAvailable() && !state.live.busy && selected?.status === 'active' && !selected.pendingWrites && selectedInteractionAllowed()) callController?.start(action === 'call-video' ? 'video' : 'audio').catch(() => {});
 }
 else if (action === 'call-accept') {
   const selected = state.live.conversations.find(c => c.id === state.live.selected);
   if (callPageAvailable() && !state.live.busy && selected?.status === 'active' && !selected.pendingWrites && selectedInteractionAllowed()) callController?.accept().catch(() => {});
 }
 else if (action === 'call-hangup') { callController?.hangUp(); }
 else if (action === 'call-mic-toggle' || action === 'call-camera-toggle') {
   const kind = action === 'call-mic-toggle' ? 'audio' : 'video';
   const media = callController?.getState().localMedia?.[kind];
   if (media?.available) callController.setLocalEnabled(kind, !media.enabled);
 }
 else if (action === 'live-accept') { respondToLiveRequest('active'); }
 else if (action === 'live-close') { respondToLiveRequest('closed'); }
 else if (action === 'live-retry') { if (state.user && !state.live.ready && !state.live.connecting) initializeLive(state.user); }
 else if (action === 'block-retry') { if (EXPERIMENTAL_BLOCKING_ENABLED && state.user && blockController) blockController.setUser(state.user.uid); }
 else if (action === 'block-add' || action === 'block-remove') { changeBlock(action === 'block-add'); }
 else if (action === 'sign-in') { beginSignIn(); }
 else if (action === 'sign-out') { beginSignOut(); }
 else if (action === 'admin-check') { checkAdmin(); }
 else if (action === 'spark-reauth') { reauthenticateSparkAdmin(); }
 else if (action === 'admin-apply') { applyAdmin(); }
 else if (action === 'spark-accept') { acceptSparkAdmin(); }
 else if (action === 'back') { state.mobileChat = false; render(); }
 else if (action === 'close-modal') { if (event.target.closest('[data-modal]') && !event.target.closest('button')) return; state.modal = null; render(); }
 else if (action === 'emoji') { state.draft += ' 😊'; render(); document.querySelector('#message-input')?.focus(); }
 else if (action) { state.modal = action; render(); document.querySelector('.modal-close')?.focus(); }
});
root.addEventListener('input', event => {
 if (event.target.id === 'message-input') state.draft = event.target.value;
 if (event.target.id === 'live-message-input' && state.live.selected) {
   state.live.messageDraft = event.target.value;
   liveDrafts.setText(state.live.selected, event.target.value);
 }
 if (event.target.id === 'live-peer-input') state.live.peerDraft = event.target.value;
 if (event.target.id === 'ai-input') state.ai.draft = event.target.value;
 if (event.target.id === 'admin-command') { state.admin.command = event.target.value; state.admin.proposal = null; }
 if (event.target.id === 'admin-target') state.admin.target = event.target.value;
 if (event.target.id === 'admin-confirm') { state.admin.confirm = event.target.checked; render(); }
 if (event.target.id === 'search') { const pos = event.target.selectionStart; state.search = event.target.value; render(); const search = document.querySelector('#search'); search.focus(); try { search.setSelectionRange(pos,pos); } catch {} }
});
root.addEventListener('submit', event => {
 if (event.target.id === 'ai-form') { event.preventDefault(); submitAi(); return; }
 if (event.target.id === 'admin-command-form') { event.preventDefault(); previewAdmin(); return; }
 if (event.target.id === 'admin-transfer-form') { event.preventDefault(); transferAdmin(); return; }
 if (event.target.id === 'live-new-form') { event.preventDefault(); startLiveConversation(); return; }
 if (event.target.id === 'live-message-form') { event.preventDefault(); submitLiveMessage(); return; }
 if (event.target.id !== 'message-form') return;
 event.preventDefault(); const text = state.draft.trim(); if (!text) return;
 const time = new Intl.DateTimeFormat('en', { hour: 'numeric', minute: '2-digit' }).format(new Date());
 if (!Array.isArray(state.saved[state.active])) state.saved[state.active] = [];
 state.saved[state.active].push({ from: 'me', text, time });
 if (state.active === 'saira') state.saved.saira.push({ from: 'them', text: sairaReply(text), time });
 try { localStorage.setItem(key, JSON.stringify(state.saved)); } catch { /* demo remains usable in memory */ }
 state.draft = ''; render(true); document.querySelector('#message-input')?.focus();
});
document.addEventListener('keydown', event => { if (event.key === 'Escape' && state.modal) { state.modal = null; render(); } if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') { event.preventDefault(); document.querySelector('#search')?.focus(); } });
render(true);

let liveGeneration = 0;
// Distinguish successive subscriptions even when the user reopens the same ID.
let liveThreadGeneration = 0;
let stopConversations = null;
let messageFeed = null;
let liveApi = null;
let callController = null;
let blockController = null;
let peerProfileController = null;
let callInitGeneration = 0;
let cloudGeneration = 0;
let pageActive = true;
// This lab has no background-call or notification implementation. Invalidate
// pending imports and dispose discovery as well as active media while hidden.
// Browser suspension can still delay or prevent lifecycle events.
function callPageAvailable() {
  return canUseCallPage({enabled:EXPERIMENTAL_CALLS_ENABLED, pageActive, hidden:document.hidden, view:state.view, authenticated:isCurrentAuthUser(state.user)});
}
function stopCallsForPage() {
  callInitGeneration++;
  const old = callController; callController = null; state.live.call = null;
  try { old?.dispose(); } catch { /* Dispose attempts local media teardown even if discovery cleanup fails. */ }
}
function resumeCallsForPage() {
  if (callPageAvailable() && state.live.selected && !callController && state.live.call?.status !== 'loading') initializeCalls(state.live.selected, liveGeneration, liveThreadGeneration);
}
document.addEventListener('visibilitychange', () => {
  if (document.hidden) stopCallsForPage();
  else { resumeCallsForPage(); if (state.view === 'live') render(); }
});
window.addEventListener('pagehide', () => { pageActive = false; stopCallsForPage(); });
window.addEventListener('pageshow', () => { pageActive = true; resumeCallsForPage(); });

watchAuth(user => {
  resetLive();
  cloudGeneration++;
  state.ai = { draft: '', messages: [], busy: false, error: '' };
  state.admin = { status: 'idle', error: '', notice: '', command: '', proposal: null, target: '', confirm: false, busy: false };
  state.user = user;
  state.authReady = true;
  render();
  if (user && LIVE_CHAT_ENABLED) initializeLive(user);
  if (user && state.view === 'admin' && (SPARK_ADMIN_ENABLED || (ADMIN_BACKEND_ENABLED && APP_CHECK_SITE_KEY))) checkAdmin();
}, error => {
  state.authReady = true;
  state.authError = authErrorMessage(error?.code);
  state.modal = 'authError';
  render();
});

async function beginSignIn() {
  if (state.authLoading || !state.authReady) return;
  state.authLoading = true;
  render();
  try {
    await googleSignIn();
  } catch (error) {
    state.authError = authErrorMessage(error?.code);
    state.modal = 'authError';
  } finally {
    state.authLoading = false;
    render();
  }
}

async function beginSignOut() {
  if (state.authLoading) return;
  if ((state.live.busy || state.live.connecting || state.live.block?.status === 'saving') && !window.confirm('A live chat action is still waiting. Signing out will not necessarily cancel a request already sent; it may still succeed later. Check its outcome before retrying. Sign out anyway?')) return;
  // Do not keep the camera or microphone active while sign-out waits on the network.
  callController?.hangUp();
  state.authLoading = true;
  render();
  try {
    await googleSignOut();
  } catch (error) {
    state.authError = authErrorMessage(error?.code);
    state.modal = 'authError';
  } finally {
    state.authLoading = false;
    render();
  }
}

// Listener generations prevent stale responses from a signed-out account from reappearing.
function resetLive() {
  slowLiveWrite.reset();
  liveDrafts.clear();
  liveGeneration++;
  liveThreadGeneration++;
  callInitGeneration++;
  disposeLiveResources({callController, blockController, stopConversations, messageFeed, peerProfileController});
  callController = null; blockController = null; stopConversations = null; messageFeed = null; peerProfileController = null; liveApi = null;
  state.live = { ready: false, connecting: false, error: '', conversations: [], selected: null, messages: [], hasOlder: false, loadingOlder: false, messagesFromCache: true, historyReset: false, peerProfile:null, peerDraft: '', messageDraft: '', busy: false, block:null };
}
// Snapshot errors are terminal. Clear previously authorized data and stop all
// listeners and local media rather than showing potentially revoked content.
function failLiveSubscription(error) {
  resetLive();
  state.live.error = `${chatErrorMessage(error)} Reconnect to try again.`;
  if (state.view === 'live') render();
}
function liveAccountGuard(user, generation) {
  return createAccountOperationGuard(() => generation === liveGeneration && state.user === user && isCurrentAuthUser(user));
}
async function initializeLive(user) {
  if (state.live.connecting || state.live.ready) return;
  state.live.connecting = true; state.live.error = '';
  if (state.view === 'live') render();
  const generation = liveGeneration, guard = liveAccountGuard(user, generation);
  try {
    const api = await import('./live-chat.js');
    guard.run(() => { liveApi = api; });
    await guard.run(() => api.registerProfile(user));
    guard.run(() => {});
    if (EXPERIMENTAL_BLOCKING_ENABLED) {
      const {createBrowserBlockController} = await import('./block-feature.js');
      guard.run(() => {});
      blockController = createBrowserBlockController(block => {
        if (generation !== liveGeneration) return;
        const previouslyAllowed = selectedInteractionAllowed();
        state.live.block = block;
        const selected = state.live.conversations.find(item => item.id === state.live.selected);
        const allowed = selectedInteractionAllowed();
        if (!allowed) { callInitGeneration++; callController?.dispose(); callController = null; state.live.call = null; }
        else if (!previouslyAllowed && selected?.status === 'active' && !selected.pendingWrites && EXPERIMENTAL_CALLS_ENABLED) initializeCalls(selected.id, liveGeneration, liveThreadGeneration);
        if (state.view === 'live') render();
      });
      blockController.setUser(user.uid);
    }
    state.live.ready = true; state.live.connecting = false;
    stopConversations = liveApi.watchConversations(user.uid, conversations => {
      if (generation !== liveGeneration) return;
      const oldSelected = state.live.conversations.find(c => c.id === state.live.selected);
      state.live.conversations = conversations;
      const selected = conversations.find(c => c.id === state.live.selected);
      if (state.live.selected && !selected) { liveThreadGeneration++; callInitGeneration++; callController?.dispose(); callController = null; messageFeed?.stop(); messageFeed = null; peerProfileController?.dispose(); peerProfileController = null; state.live.peerProfile = null; state.live.selected = null; state.live.messages = []; state.live.hasOlder = false; state.live.loadingOlder = false; state.live.messagesFromCache = true; state.live.historyReset = false; state.live.messageDraft = ''; state.live.call = null; }
      else if (selected) {
        const wasReady = oldSelected?.status === 'active' && !oldSelected.pendingWrites;
        const isReady = selected.status === 'active' && !selected.pendingWrites;
        if (wasReady && !isReady) { callInitGeneration++; callController?.dispose(); callController = null; state.live.call = null; }
        else if (!wasReady && isReady && EXPERIMENTAL_CALLS_ENABLED) initializeCalls(selected.id, liveGeneration, liveThreadGeneration);
      }
      if (state.view === 'live') render();
    }, error => { if (generation === liveGeneration) failLiveSubscription(error); });
    if (state.view === 'live') render();
  } catch (error) {
    if (generation !== liveGeneration || state.user !== user || !isCurrentAuthUser(user)) return;
    failLiveSubscription(error);
  }
}
function selectedInteractionAllowed() {
  if (!EXPERIMENTAL_BLOCKING_ENABLED) return true;
  const conversation = state.live.conversations.find(item => item.id === state.live.selected);
  const uid = state.user?.uid;
  const peer = conversation?.participants?.find(id => id !== uid);
  return !!peer && state.live.block?.status === 'ready' && !state.live.block.ids.includes(peer);
}
function selectLiveConversation(id) {
  if (!state.live.conversations.some(c => c.id === id) || !liveApi) return;
  liveThreadGeneration++;
  callController?.dispose(); callController = null;
  messageFeed?.stop(); messageFeed = null;
  peerProfileController?.dispose(); peerProfileController = null;
  state.live.peerProfile = null;
  const draft = liveDrafts.read(id);
  state.live.selected = id; state.live.messages = []; state.live.hasOlder = false; state.live.loadingOlder = false; state.live.messagesFromCache = true; state.live.historyReset = false; state.live.messageDraft = draft.text; state.live.error = draft.error; state.live.call = null;
  const generation = liveGeneration, threadGeneration = liveThreadGeneration;
  messageFeed = liveApi.watchMessages(id, page => {
    if (generation !== liveGeneration || threadGeneration !== liveThreadGeneration || state.live.selected !== id) return;
    const previousCount = state.live.messages.length, wasLoading = state.live.loadingOlder;
    const old = document.querySelector('#live-messages');
    const oldTop = old?.scrollTop ?? 0, oldHeight = old?.scrollHeight ?? 0;
    const nearBottom = old ? old.scrollHeight - old.scrollTop - old.clientHeight < 60 : true;
    state.live.messages = page.messages; state.live.hasOlder = page.hasMore; state.live.loadingOlder = page.loading; state.live.messagesFromCache = page.fromCache; state.live.historyReset = page.historyReset;
    if (state.view === 'live') {
      render();
      const el = document.querySelector('#live-messages');
      if (el) {
        if (wasLoading && !page.loading && page.messages.length > previousCount) el.scrollTop = oldTop + (el.scrollHeight - oldHeight);
        else if (!previousCount || nearBottom) el.scrollTop = el.scrollHeight;
        else el.scrollTop = oldTop;
      }
    }
  }, error => { if (generation === liveGeneration && threadGeneration === liveThreadGeneration && state.live.selected === id) { state.live.error = chatErrorMessage(error); if (state.view === 'live') render(); } },
  error => { if (generation === liveGeneration && threadGeneration === liveThreadGeneration && state.live.selected === id) failLiveSubscription(error); });
  if (generation !== liveGeneration || threadGeneration !== liveThreadGeneration || !liveApi || state.live.selected !== id) return;
  const selected = state.live.conversations.find(c => c.id === id);
  const peerUid = selected?.participants?.find(uid => uid !== state.user?.uid);
  if (peerUid && state.user?.uid) {
    peerProfileController = createPeerProfileController({subscribe:liveApi.watchPeerProfile, onChange:profile => {
      if (generation !== liveGeneration || threadGeneration !== liveThreadGeneration || state.live.selected !== id) return;
      state.live.peerProfile = profile;
      if (state.view === 'live') render();
    }});
    try { peerProfileController.select(peerUid); } catch { peerProfileController.dispose(); peerProfileController = null; }
  }
  if (EXPERIMENTAL_CALLS_ENABLED && selected?.status === 'active' && !selected.pendingWrites) initializeCalls(id, generation, threadGeneration);
  render();
}
async function initializeCalls(id, generation, threadGeneration) {
  const conversation = state.live.conversations.find(item => item.id === id);
  const uid = state.user?.uid;
  const peerUid = conversation?.participants?.find(participant => participant !== uid);
  if (!callPageAvailable() || !state.live.ready || state.live.selected !== id || callController || state.live.call?.status === 'loading' || !uid || !peerUid || conversation.status !== 'active' || conversation.pendingWrites || !selectedInteractionAllowed()) return;
  const initGeneration = ++callInitGeneration;
  state.live.call = {status:'loading',incoming:[],error:''};
  try {
    const { createBrowserCallController } = await import('./call-feature.js');
    if (!callPageAvailable() || initGeneration !== callInitGeneration || generation !== liveGeneration || threadGeneration !== liveThreadGeneration || !selectedInteractionAllowed()) return;
    callController = createBrowserCallController(call => {
      if (!callPageAvailable() || initGeneration !== callInitGeneration || generation !== liveGeneration || threadGeneration !== liveThreadGeneration) return;
      state.live.call = call;
      if (state.view === 'live') render();
    }, () => callPageAvailable() && initGeneration === callInitGeneration && generation === liveGeneration && threadGeneration === liveThreadGeneration);
    callController.setConversation({id,uid,peerUid});
  } catch {
    if (!callPageAvailable() || initGeneration !== callInitGeneration || generation !== liveGeneration || threadGeneration !== liveThreadGeneration) return;
    callController?.dispose(); callController = null;
    state.live.call = {status:'unavailable',incoming:[],error:'Calling is unavailable or not configured.'};
    if (state.view === 'live') render();
  }
}
async function changeBlock(wantBlocked) {
  if (!EXPERIMENTAL_BLOCKING_ENABLED || !state.user || !state.live.ready || state.live.busy || state.live.block?.status !== 'ready' || !blockController) return;
  const conversation = state.live.conversations.find(item => item.id === state.live.selected);
  const uid = state.user.uid, guard = liveAccountGuard(state.user, liveGeneration);
  const peer = conversation?.participants?.find(id => id !== uid);
  if (!peer || conversation.pendingWrites || !['pending','active','closed'].includes(conversation.status)) return;
  const message = wantBlocked
    ? 'Block this account and close this conversation? History remains visible. Calls on other devices may continue; the block is not confirmed until the server responds.'
    : 'Unblock this account? This closed conversation stays closed, but other existing active conversations may allow contact again.';
  if (!window.confirm(message)) return;
  if (wantBlocked) callController?.hangUp();
  try {
    if (wantBlocked) await guard.run(() => blockController.block(peer, conversation));
    else await guard.run(() => blockController.unblock(peer));
  } catch { /* Controller reports a generic error or an obsolete action was ignored. */ }
}
async function startLiveConversation() {
  if (!state.user || !state.live.ready || state.live.busy) return;
  const generation = liveGeneration, user = state.user, guard = liveAccountGuard(user, generation); const uid = user.uid; const peer = state.live.peerDraft;
  if (EXPERIMENTAL_BLOCKING_ENABLED && (state.live.block?.status !== 'ready' || state.live.block.ids.includes(peer.trim()))) {
    state.live.error = 'Confirm blocked-user status and unblock this account before requesting a conversation.'; render(); return;
  }
  state.live.busy = true; state.live.error = ''; state.live.pendingNotice = false;
  const finishNotice = slowLiveWrite.begin(); render();
  try {
    await guard.run(() => liveApi.createConversation(uid, peer, guard));
    if (generation === liveGeneration) state.live.peerDraft = '';
  } catch (error) { if (generation === liveGeneration) state.live.error = EXPERIMENTAL_REQUEST_COOLDOWN && error?.code === 'permission-denied' ? 'Request was not sent. Wait a moment and check that contact is allowed.' : chatErrorMessage(error); }
  finally { finishNotice(); if (generation === liveGeneration) { state.live.busy = false; render(); } }
}
async function respondToLiveRequest(status) {
  if (!state.user || !state.live.ready || state.live.busy) return;
  const conversation = state.live.conversations.find(c => c.id === state.live.selected);
  if (!conversation || conversation.pendingWrites) return;
  if (status === 'active' && (conversation.status !== 'pending' || conversation.createdBy === state.user.uid || !selectedInteractionAllowed())) return;
  if (status === 'closed' && !['pending','active'].includes(conversation.status)) return;
  if (status === 'closed' && !window.confirm('Close this conversation? This does not block future requests.')) return;
  if (status === 'closed') callController?.hangUp();
  const generation = liveGeneration, threadGeneration = liveThreadGeneration, id = conversation.id, guard = liveAccountGuard(state.user, generation);
  state.live.busy = true; state.live.error = ''; state.live.pendingNotice = false;
  const finishNotice = slowLiveWrite.begin(); render();
  try { await guard.run(() => liveApi.respondToConversation(id, status)); }
  catch (error) { if (generation === liveGeneration && threadGeneration === liveThreadGeneration) state.live.error = chatErrorMessage(error); }
  finally { finishNotice(); if (generation === liveGeneration) { state.live.busy = false; render(); } }
}
async function submitLiveMessage() {
  const conversation = state.live.conversations.find(c => c.id === state.live.selected);
  if (!state.user || !state.live.ready || !conversation || conversation.status !== 'active' || conversation.pendingWrites || state.live.busy || !selectedInteractionAllowed()) return;
  const generation = liveGeneration, user = state.user, guard = liveAccountGuard(user, generation); const id = state.live.selected; const text = state.live.messageDraft; const uid = user.uid;
  liveDrafts.setError(id, '');
  state.live.busy = true; state.live.error = ''; state.live.pendingNotice = false;
  const finishNotice = slowLiveWrite.begin(); render();
  try {
    await guard.run(() => liveApi.sendMessage(id, uid, text));
    if (generation === liveGeneration && liveDrafts.acknowledge(id, text) && state.live.selected === id && state.live.messageDraft === text) state.live.messageDraft = '';
  } catch (error) {
    if (generation === liveGeneration) {
      const message = EXPERIMENTAL_MESSAGE_COOLDOWN && error?.code === 'permission-denied' ? 'Message was not sent. Wait a moment and check that this chat is active and allowed.' : chatErrorMessage(error);
      liveDrafts.setError(id, message);
      if (state.live.selected === id) state.live.error = message;
    }
  }
  finally { finishNotice(); if (generation === liveGeneration) { state.live.busy = false; render(); } }
}

if ('serviceWorker' in navigator && window.isSecureContext) {
  window.addEventListener('load', () => navigator.serviceWorker.register('/sw.js').catch(() => {}));
}

function cloudMessage(error) {
  if (error?.code === 'functions/permission-denied') return 'This account is not authorized for that action.';
  if (error?.code === 'functions/resource-exhausted') return 'Please wait a moment before trying again.';
  if (error?.code === 'functions/failed-precondition') return 'The request could not be completed. For admin transfer, sign in again and confirm an active verified account.';
  return 'The service is unavailable or not configured. Please try again later.';
}
function sparkAdminError(error) {
  return error?.code === 'permission-denied'
    ? 'Access denied or verification is no longer recent. Verify your Google account again and check your access before retrying.'
    : 'The service is unavailable or the change could not be confirmed. Check the result before retrying.';
}
function accountGuard(user, generation) {
  return createAccountOperationGuard(() => generation === cloudGeneration && state.user === user && isCurrentAuthUser(user));
}
async function cloudCall(name, payload, guard) {
  const { callCloud } = await import('./cloud-service.js');
  return guard.run(() => callCloud(name, payload));
}
async function submitAi() {
  if (!SAIRA_AI_ENABLED || !APP_CHECK_SITE_KEY || !state.user || state.ai.busy) return;
  const question = state.ai.draft.trim(); if (!question || question.length > 1000) return;
  const generation = cloudGeneration, guard = accountGuard(state.user, generation); state.ai.busy = true; state.ai.error = ''; render();
  try {
    const result = SAIRA_PROVIDER === 'firebase-ai'
      ? await import('./firebase-ai-service.js').then(api => guard.run(() => api.askFirebaseSaira(question)))
      : await cloudCall('askSaira', {question}, guard);
    if (generation !== cloudGeneration) return;
    if (typeof result?.answer !== 'string') throw new Error('Invalid response');
    state.ai.messages.push({question, answer: result.answer}); state.ai.draft = '';
  } catch (error) { if (generation === cloudGeneration) state.ai.error = cloudMessage(error); }
  finally { if (generation === cloudGeneration) { state.ai.busy = false; render(); } }
}
async function reauthenticateSparkAdmin() {
  if (!SPARK_ADMIN_ENABLED || !state.user || state.admin.busy || state.authLoading || state.admin.status === 'checking') return;
  const generation = cloudGeneration, user = state.user;
  state.admin.busy = true; state.admin.error = ''; state.admin.notice = ''; render();
  try {
    await googleReauthenticate();
    if (generation !== cloudGeneration || state.user !== user) return;
    // A fresh sign-in is not authorization. Query the server again; the rules
    // still verify the identity, role, recent auth_time and each privileged write.
    state.admin.notice = 'Google verification completed. Server authorization is still required.';
    state.admin.status = 'idle'; state.admin.busy = false;
    await checkAdmin();
  } catch {
    if (generation === cloudGeneration && state.user === user) state.admin.error = 'Could not verify this Google account. Try again with the same account.';
  } finally {
    if (generation === cloudGeneration && state.user === user) { state.admin.busy = false; render(); }
  }
}
async function checkAdmin() {
  if ((!SPARK_ADMIN_ENABLED && (!ADMIN_BACKEND_ENABLED || !APP_CHECK_SITE_KEY)) || !state.user || state.admin.status === 'checking') return;
  const generation = cloudGeneration, user = state.user, guard = accountGuard(user, generation); state.admin.status = 'checking'; state.admin.error = ''; render();
  try {
    if (SPARK_ADMIN_ENABLED) {
      const api = await import('./spark-admin-service.js');
      const result = await guard.run(() => api.getSparkAdminStatus(user, guard));
      const delegations = result.role === 'owner' && result.status === 'authorized' ? await guard.run(() => api.listSparkDelegations()) : [];
      if (generation === cloudGeneration) { state.admin.status = result.status; state.admin.role = result.role; state.admin.delegations = delegations; }
    } else {
      const result = await cloudCall('adminStatus', {}, guard);
      if (generation === cloudGeneration) state.admin.status = result?.isAdmin === true ? 'authorized' : 'forbidden';
    }
  } catch (error) { if (generation === cloudGeneration) { state.admin.status = 'error'; state.admin.error = SPARK_ADMIN_ENABLED ? sparkAdminError(error) : cloudMessage(error); } }
  finally { if (generation === cloudGeneration) render(); }
}
async function previewAdmin() {
  if (state.admin.status !== 'authorized' || state.admin.busy || !state.user) return;
  const command = state.admin.command.trim(); if (!command || command.length > 1000) return;
  const generation = cloudGeneration, guard = accountGuard(state.user, generation); state.admin.busy = true; state.admin.error = ''; state.admin.notice = ''; state.admin.proposal = null; render();
  try {
    const proposal = SPARK_ADMIN_ENABLED ? (await import('./spark-admin-commands.js')).parseSparkCommand(command) : (await cloudCall('previewAdminUpdate', {command}, guard))?.proposal;
    if (generation === cloudGeneration) state.admin.proposal = proposal || {action:'unsupported'};
  }
  catch (error) { if (generation === cloudGeneration) state.admin.error = cloudMessage(error); }
  finally { if (generation === cloudGeneration) { state.admin.busy = false; render(); } }
}
async function applyAdmin() {
  if (state.admin.status !== 'authorized' || state.admin.busy || state.admin.proposal?.action !== 'set_welcome' || !state.user) return;
  const generation = cloudGeneration, user = state.user, guard = accountGuard(user, generation); const proposal = state.admin.proposal, command = state.admin.command; state.admin.busy = true; state.admin.error = ''; render();
  try {
    if (SPARK_ADMIN_ENABLED) { const api = await import('./spark-admin-service.js'); await guard.run(() => api.applySparkWelcome(user, command, true)); }
    else await cloudCall('applyAdminUpdate', {proposal, confirmed:true}, guard);
    if (generation === cloudGeneration) { state.admin.proposal = null; state.admin.notice = 'Welcome message updated.'; }
  }
  catch (error) { if (generation === cloudGeneration) state.admin.error = SPARK_ADMIN_ENABLED ? sparkAdminError(error) : cloudMessage(error); }
  finally { if (generation === cloudGeneration) { state.admin.busy = false; render(); } }
}
async function transferAdmin() {
  if (state.admin.status !== 'authorized' || state.admin.busy || !state.admin.confirm || !state.user) return;
  const targetUid = state.admin.target.trim(); if (!targetUid || targetUid.length > 128) return;
  const generation = cloudGeneration, user = state.user, guard = accountGuard(user, generation); state.admin.busy = true; state.admin.error = ''; render();
  try {
    if (SPARK_ADMIN_ENABLED) {
      if (state.admin.role !== 'owner') throw new Error('Owner access required.');
      const email = targetUid.toLowerCase();
      const api = await import('./spark-admin-service.js');
      await guard.run(() => api.inviteSparkDelegate(user, email, true, guard));
      const delegations = await guard.run(() => api.listSparkDelegations());
      if (generation === cloudGeneration) { state.admin.delegations = delegations; state.admin.target = ''; state.admin.notice = 'Invitation saved; the recipient must sign in and accept. No email was sent.'; }
    } else {
      await cloudCall('transferAdmin', {targetUid, confirmed:true}, guard);
      if (generation === cloudGeneration) { state.admin.status = 'forbidden'; state.admin.error = 'Admin access was transferred. This account no longer has admin access.'; }
    }
  }
  catch (error) { if (generation === cloudGeneration) state.admin.error = SPARK_ADMIN_ENABLED ? sparkAdminError(error) : cloudMessage(error); }
  finally { if (generation === cloudGeneration) { state.admin.busy = false; state.admin.confirm = false; render(); } }
}
async function acceptSparkAdmin() {
  if (!SPARK_ADMIN_ENABLED || state.admin.status !== 'invited' || state.admin.busy || !state.user) return;
  if (!window.confirm('Accept delegated admin access? The owner keeps their access.')) return;
  const generation = cloudGeneration, user = state.user, guard = accountGuard(user, generation); state.admin.busy = true; state.admin.error = ''; render();
  try {
    const api = await import('./spark-admin-service.js');
    await guard.run(() => api.acceptSparkDelegation(user, true));
    if (generation === cloudGeneration) { state.admin.status = 'authorized'; state.admin.role = 'delegate'; state.admin.notice = 'Delegated access accepted.'; }
  } catch (error) { if (generation === cloudGeneration) state.admin.error = 'Could not accept. Verify your Google account again and check whether the invitation is still active.'; }
  finally { if (generation === cloudGeneration) { state.admin.busy = false; render(); } }
}
async function revokeSparkAdmin(email) {
  if (!SPARK_ADMIN_ENABLED || state.admin.status !== 'authorized' || state.admin.role !== 'owner' || state.admin.busy || !state.user) return;
  if (!window.confirm(`Revoke the invitation or access for ${email}?`)) return;
  const generation = cloudGeneration, guard = accountGuard(state.user, generation); state.admin.busy = true; state.admin.error = ''; render();
  try {
    const api = await import('./spark-admin-service.js');
    await guard.run(() => api.revokeSparkDelegate(email, true));
    const delegations = await guard.run(() => api.listSparkDelegations());
    if (generation === cloudGeneration) { state.admin.delegations = delegations; state.admin.notice = 'Access revoked.'; }
  } catch (error) { if (generation === cloudGeneration) state.admin.error = 'Could not confirm revocation. Verify your Google account again and check the current access before retrying.'; }
  finally { if (generation === cloudGeneration) { state.admin.busy = false; render(); } }
}
if (SPARK_ADMIN_ENABLED) {
  import('./spark-admin-service.js').then(({watchSparkWelcome}) => watchSparkWelcome(value => { state.publicWelcome = value; render(); }, () => {})).catch(() => {});
} else if (ADMIN_BACKEND_ENABLED && APP_CHECK_SITE_KEY) {
  import('./cloud-service.js').then(({watchPublicWelcome}) => watchPublicWelcome(value => { state.publicWelcome = value; render(); }, () => {})).catch(() => {});
}
