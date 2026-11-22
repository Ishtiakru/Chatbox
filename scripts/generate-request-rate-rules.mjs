// Isolated review-only candidate; this script neither changes deployment config
// nor deploys rules. This layers invitation throttling on message throttling.
import {readFileSync,writeFileSync} from 'node:fs';
const marker='    // Public display settings can only be changed by the trusted backend.';
const creationTail='        && exists(/databases/$(database)/documents/profiles/$(request.resource.data.participants[1]));';
const block=`    // Experimental per-account request cooldown; not comprehensive spam control.
    function requestThrottlePath(uid) { return /databases/$(database)/documents/requestThrottle/$(uid); }
    function requestedConversationPath(conversationId) { return /databases/$(database)/documents/conversations/$(conversationId); }
    function hasRequestReservation(conversationId) {
      let path = requestThrottlePath(request.auth.uid);
      return getAfter(path).data.conversationId == conversationId
        && getAfter(path).data.lastRequestedAt == request.time
        && (!exists(path) || get(path).data.lastRequestedAt < request.time);
    }
    function validRequestReservation(uid) {
      let data = request.resource.data;
      let target = requestedConversationPath(data.conversationId);
      return signedIn() && request.auth.uid == uid
        && data.keys().hasAll(['conversationId', 'lastRequestedAt'])
        && data.keys().hasOnly(['conversationId', 'lastRequestedAt'])
        && data.conversationId is string && data.conversationId.size() > 0 && data.conversationId.size() <= 128
        && data.conversationId.matches('^[^/]+$')
        && data.lastRequestedAt == request.time
        && !exists(target)
        && getAfter(target).data.createdBy == uid
        && getAfter(target).data.status == 'pending'
        && getAfter(target).data.createdAt == request.time;
    }
    match /requestThrottle/{uid} {
      allow get: if signedIn() && request.auth.uid == uid;
      allow list, delete: if false;
      allow create: if validRequestReservation(uid);
      allow update: if validRequestReservation(uid)
        && request.time >= resource.data.lastRequestedAt + duration.value(30, 's');
    }

`;
function replaceOnce(input,needle,replacement) {
  const i=input.indexOf(needle);
  if (i<0 || input.indexOf(needle,i+needle.length)!==-1) throw new Error('Expected exactly one rules marker.');
  return input.slice(0,i)+replacement+input.slice(i+needle.length);
}
export function generateRequestRateRules(input) {
  if (input.includes('match /requestThrottle/{uid}')) throw new Error('Request cooldown already present.');
  if (!input.includes('match /messageThrottle/{uid}')) throw new Error('Message cooldown candidate required.');
  return replaceOnce(replaceOnce(input,marker,block+marker),creationTail,creationTail.slice(0,-1)+'\n        && hasRequestReservation(conversationId);');
}
if (process.argv[1] && import.meta.url===new URL(process.argv[1],`file://${process.cwd()}/`).href) {
  writeFileSync('firestore.request-limited-experimental.rules',generateRequestRateRules(readFileSync('firestore.rate-limited-experimental.rules','utf8')));
  console.log('Wrote isolated request cooldown candidate; no deployment performed.');
}
