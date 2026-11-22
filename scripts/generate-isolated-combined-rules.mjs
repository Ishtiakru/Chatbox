// Review-only two-account admin/chat/call candidate. Never deploys or writes
// Firebase configuration. Public settings remain readable by everyone.
import {readFileSync} from 'node:fs';
import {createHash} from 'node:crypto';
import {pathToFileURL} from 'node:url';

const EXPECTED_SHA256 = '1e178bb0ba45c4fa036858566a7178c3b2a7b086454f4a06253bb20ccb835c94';
export function restrictedCombinedTestRules(source, firstUid, secondUid) {
  const valid = uid => typeof uid === 'string' && /^[A-Za-z0-9_-]{1,128}$/.test(uid);
  if (!valid(firstUid) || !valid(secondUid) || firstUid === secondUid) throw new Error('Two distinct valid test UIDs are required.');
  if (typeof source !== 'string' || createHash('sha256').update(source).digest('hex') !== EXPECTED_SHA256) {
    throw new Error('Combined rules changed; review before generating restricted test rules.');
  }
  const original = 'function signedIn() { return request.auth != null; }';
  if (source.split(original).length !== 2) throw new Error('Unexpected combined rules; cannot safely restrict accounts.');
  return source.replace(original, `function signedIn() { return request.auth != null && request.auth.uid in ['${firstUid}', '${secondUid}']; }`);
}
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    if (process.argv.length !== 4) throw new Error('Usage: node scripts/generate-isolated-combined-rules.mjs FIRST_UID SECOND_UID');
    process.stdout.write(restrictedCombinedTestRules(readFileSync(new URL('../firestore.combined-experimental.rules', import.meta.url),'utf8'), process.argv[2], process.argv[3]));
  } catch (error) { console.error(error.message); process.exitCode = 1; }
}
