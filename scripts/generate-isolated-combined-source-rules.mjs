// Isolated, two-account source-handover candidate only. No Firebase writes.
// The base generator checks the exact reviewed combined template hash first.
import { readFileSync } from 'node:fs';
import { pathToFileURL } from 'node:url';
import { restrictedCombinedTestRules } from './generate-isolated-combined-rules.mjs';
import { sparkSourceRules } from './generate-spark-source-rules.mjs';

export function restrictedCombinedSourceTestRules(source, firstUid, secondUid) {
  return sparkSourceRules(restrictedCombinedTestRules(source, firstUid, secondUid));
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    if (process.argv.length !== 4) throw new Error('Usage: node scripts/generate-isolated-combined-source-rules.mjs FIRST_UID SECOND_UID');
    process.stdout.write(restrictedCombinedSourceTestRules(readFileSync(new URL('../firestore.combined-experimental.rules', import.meta.url), 'utf8'), process.argv[2], process.argv[3]));
  } catch (error) { console.error(error.message); process.exitCode = 1; }
}
