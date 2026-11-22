// Generates review-only test rules. This does not deploy anything.
// Source is the base chat candidate, not the admin or call candidates.
import { readFileSync } from 'node:fs';
import { createHash } from 'node:crypto';
import { pathToFileURL } from 'node:url';

// Changing the base rules requires a fresh security review before updating this.
const EXPECTED_BASE_SHA256 = '6b7484562a90e3498dc3fa519acb2226c66ff80a56a840ccd9f774dd5b28df28';

export function restrictedTestRules(source, firstUid, secondUid) {
  const valid = uid => typeof uid === 'string' && /^[A-Za-z0-9_-]{1,128}$/.test(uid);
  if (!valid(firstUid) || !valid(secondUid) || firstUid === secondUid) {
    throw new Error('Two distinct, valid test Firebase UIDs are required.');
  }
  if (typeof source !== 'string') throw new Error('Base rules are required.');
  if (createHash('sha256').update(source).digest('hex') !== EXPECTED_BASE_SHA256) {
    throw new Error('Base rules changed; review before generating restricted test rules.');
  }
  const from = 'function signedIn() { return request.auth != null; }';
  if (source.split(from).length !== 2) throw new Error('Unexpected base rules: cannot safely restrict accounts.');
  const allowed = `function signedIn() { return request.auth != null && request.auth.uid in ['${firstUid}', '${secondUid}']; }`;
  return source.replace(from, allowed);
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    if (process.argv.length !== 4) throw new Error('Usage: node scripts/generate-isolated-rules.mjs FIRST_UID SECOND_UID');
    const rules = readFileSync(new URL('../firestore.rules', import.meta.url), 'utf8');
    process.stdout.write(restrictedTestRules(rules, process.argv[2], process.argv[3]));
  } catch (error) {
    console.error(error.message);
    process.exitCode = 1;
  }
}
