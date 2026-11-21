// Produce a review-only combination of the two experimental rule candidates.
// This never changes firebase.json or deploys anything. Fail closed if either
// candidate diverges from the common base, rather than silently merging rules.
import { readFileSync, writeFileSync } from 'node:fs';

export function combineRules(base, spark, calls) {
  const callStart = '      // Experimental call signaling; deliberately not selected by firebase.json.';
  const callEnd = '      match /messages/{messageId} {';
  const adminEnd = '    // Public display settings can only be changed by the trusted backend.';
  const uniqueIndex = (text, marker) => {
    const index = text.indexOf(marker);
    if (index < 0 || text.indexOf(marker, index + marker.length) !== -1) throw new Error(`Missing or duplicate marker: ${marker}`);
    return index;
  };
  const start = uniqueIndex(calls, callStart), end = uniqueIndex(calls, callEnd);
  if (end <= start) throw new Error('Call block markers are out of order.');
  const callBlock = calls.slice(start, end);
  if (calls.slice(0, start) + calls.slice(end) !== base) throw new Error('Call candidate differs from the base outside its call block.');
  const baseSettings = uniqueIndex(base, adminEnd), sparkSettings = uniqueIndex(spark, adminEnd);
  if (spark.slice(sparkSettings) !== base.slice(baseSettings)) throw new Error('Spark candidate differs from the base after the admin block.');
  // Confirm the functions preceding the added admin block have not changed.
  const adminStart = '    // Experimental Spark-plan admin.';
  const adminIndex = uniqueIndex(spark, adminStart);
  if (spark.slice(0, adminIndex).trimEnd() !== base.slice(0, baseSettings).trimEnd()) throw new Error('Spark candidate differs from the base before the admin block.');
  const messageIndex = uniqueIndex(spark, callEnd);
  return spark.slice(0, messageIndex) + callBlock + spark.slice(messageIndex);
}

if (process.argv[1] && import.meta.url === new URL(process.argv[1], `file://${process.cwd()}/`).href) {
  const result = combineRules(...['firestore.rules','firestore.spark-admin.rules','firestore.calls.rules'].map(path => readFileSync(path,'utf8')));
  writeFileSync('firestore.combined-experimental.rules', result);
  console.log('Wrote review-only combined candidate; no deployment performed.');
}
