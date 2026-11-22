// Experimental local-emulator candidate only. No deployment or provisioning.
// This candidate is not a complete phone/email account or privacy solution.
import { createHash } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { pathToFileURL } from 'node:url';
const EXPECTED = '1e178bb0ba45c4fa036858566a7178c3b2a7b086454f4a06253bb20ccb835c94';
export function profileIdentityRules(source) {
  if (typeof source !== 'string' || createHash('sha256').update(source).digest('hex') !== EXPECTED) throw new Error('Combined rules changed; review required.');
  const marker = '    // Public display settings can only be changed by the trusted backend.';
  if (source.split(marker).length !== 2) throw new Error('Unexpected combined template.');
  const addition = `    // Experimental contact-code registry. Codes are enumerable, not secrets.
    // This does not implement phone login, number search, profile photos or
    // adequate abuse protection. Do not deploy as production rules.
    function identityVerified() {
      return signedIn() && request.auth.token.email_verified == true;
    }
    function identityProfilePath(uid) { return /databases/$(database)/documents/identityProfiles/$(uid); }
    function identityCodePath(code) { return /databases/$(database)/documents/identityCodes/$(code); }
    function codeFormat(code) { return code is string && code.matches('^[0-9]{6}$'); }
    match /identityProfiles/{uid} {
      allow get: if identityVerified() && request.auth.uid == uid;
      allow list, update, delete: if false;
      allow create: if identityVerified() && request.auth.uid == uid
        && request.resource.data.keys().hasOnly(['uid','code'])
        && request.resource.data.keys().hasAll(['uid','code'])
        && request.resource.data.uid == uid && codeFormat(request.resource.data.code)
        && !exists(identityCodePath(request.resource.data.code))
        && getAfter(identityCodePath(request.resource.data.code)).data.uid == uid
        && getAfter(identityCodePath(request.resource.data.code)).data.code == request.resource.data.code;
    }
    match /identityCodes/{code} {
      allow get: if identityVerified() && codeFormat(code);
      allow list, update, delete: if false;
      allow create: if identityVerified() && codeFormat(code)
        && request.resource.data.keys().hasOnly(['uid','code'])
        && request.resource.data.keys().hasAll(['uid','code'])
        && request.resource.data.uid == request.auth.uid && request.resource.data.code == code
        && !exists(identityProfilePath(request.auth.uid))
        && getAfter(identityProfilePath(request.auth.uid)).data.uid == request.auth.uid
        && getAfter(identityProfilePath(request.auth.uid)).data.code == code;
    }

`;
  return source.replace(marker, () => addition + marker);
}
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try { process.stdout.write(profileIdentityRules(readFileSync(new URL('../firestore.combined-experimental.rules', import.meta.url),'utf8'))); }
  catch (e) { console.error(e.message); process.exitCode=1; }
}
