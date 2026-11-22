// Generates a review-only two-account call candidate. Never deploy by default.
// The production project is not a permitted target; UID gating is not a full
// security review, abuse control, call cleanup, or end-to-end encryption.
import {readFileSync} from 'node:fs';
import {createHash} from 'node:crypto';
import {pathToFileURL} from 'node:url';

const EXPECTED_CALLS_SHA256 = '1a522d290283c60acc71d09cf407a20b733fbe8e98e242c9e257b674b7c93313';
export function restrictedCallTestRules(source, firstUid, secondUid) {
  const valid = uid => typeof uid === 'string' && /^[A-Za-z0-9_-]{1,128}$/.test(uid);
  if (!valid(firstUid) || !valid(secondUid) || firstUid === secondUid) throw new Error('Two distinct valid test UIDs are required.');
  if (typeof source !== 'string' || createHash('sha256').update(source).digest('hex') !== EXPECTED_CALLS_SHA256) {
    throw new Error('Call rules changed; review before generating restricted test rules.');
  }
  const original = 'function signedIn() { return request.auth != null; }';
  if (source.split(original).length !== 2) throw new Error('Unexpected call rules; cannot safely restrict accounts.');
  return source.replace(original, `function signedIn() { return request.auth != null && request.auth.uid in ['${firstUid}', '${secondUid}']; }`);
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    if (process.argv.length !== 4) throw new Error('Usage: node scripts/generate-isolated-call-rules.mjs FIRST_UID SECOND_UID');
    const source = readFileSync(new URL('../firestore.calls.rules', import.meta.url), 'utf8');
    process.stdout.write(restrictedCallTestRules(source, process.argv[2], process.argv[3]));
  } catch (error) {
    console.error(error.message);
    process.exitCode = 1;
  }
}
