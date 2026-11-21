// Generate a separate review-only cooldown candidate. Never deploy automatically.
import { readFileSync, writeFileSync } from 'node:fs';
const marker = '    // Public display settings can only be changed by the trusted backend.';
const messageTail = '          && request.resource.data.sentAt == request.time;';
const throttleBlock = `    // Experimental message throttle. Not a complete anti-abuse solution.
    function throttlePath(uid) { return /databases/$(database)/documents/messageThrottle/$(uid); }
    function messagePath(conversationId, messageId) {
      return /databases/$(database)/documents/conversations/$(conversationId)/messages/$(messageId);
    }
    function hasMessageReservation(conversationId, messageId) {
      let path = throttlePath(request.auth.uid);
      return getAfter(path).data.conversationId == conversationId
        && getAfter(path).data.messageId == messageId
        && getAfter(path).data.lastSentAt == request.time
        && (!exists(path) || get(path).data.lastSentAt < request.time);
    }
    function validMessageReservation(uid) {
      let data = request.resource.data;
      let target = messagePath(data.conversationId, data.messageId);
      return signedIn() && request.auth.uid == uid
        && data.keys().hasAll(['conversationId', 'messageId', 'lastSentAt'])
        && data.keys().hasOnly(['conversationId', 'messageId', 'lastSentAt'])
        && data.conversationId is string && data.conversationId.size() > 0 && data.conversationId.size() <= 128
        && data.conversationId.matches('^[^/]+$')
        && data.messageId is string && data.messageId.size() > 0 && data.messageId.size() <= 128
        && data.messageId.matches('^[^/]+$')
        && data.lastSentAt == request.time
        && !exists(target)
        && getAfter(target).data.senderId == uid
        && getAfter(target).data.sentAt == request.time;
    }
    match /messageThrottle/{uid} {
      allow get: if signedIn() && request.auth.uid == uid;
      allow list, delete: if false;
      allow create: if validMessageReservation(uid);
      allow update: if validMessageReservation(uid)
        && request.time >= resource.data.lastSentAt + duration.value(2, 's');
    }

`;
function replaceOnce(input, needle, replacement) {
  const first=input.indexOf(needle);
  if (first<0 || input.indexOf(needle, first+needle.length)!==-1) throw new Error('Expected exactly one rules marker.');
  return input.slice(0,first)+replacement+input.slice(first+needle.length);
}
export function generateRateRules(combined) {
  if (combined.includes('match /messageThrottle/{uid}')) throw new Error('Cooldown already present.');
  const withBlock=replaceOnce(combined,marker,throttleBlock+marker);
  return replaceOnce(withBlock,messageTail,'          && request.resource.data.sentAt == request.time\n          && hasMessageReservation(conversationId, messageId);');
}
if (process.argv[1] && import.meta.url === new URL(process.argv[1], `file://${process.cwd()}/`).href) {
  writeFileSync('firestore.rate-limited-experimental.rules',generateRateRules(readFileSync('firestore.combined-experimental.rules','utf8')));
  console.log('Wrote isolated cooldown candidate; firebase.json unchanged and no deployment performed.');
}
